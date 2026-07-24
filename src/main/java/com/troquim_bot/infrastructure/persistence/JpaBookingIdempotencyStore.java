package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyRecord;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.appointment.AppointmentId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotência de comando em SQL, participando da transação do caso de uso.
 *
 * <h2>Por que INSERT ... ON CONFLICT DO NOTHING</h2>
 *
 * A alternativa óbvia — {@code saveAndFlush} e capturar
 * {@code DataIntegrityViolationException} — é inutilizável aqui: no PostgreSQL, a
 * violação de constraint ABORTA a transação. Continuar nela depois do catch resulta em
 * {@code current transaction is aborted}, e o agendamento nunca comitaria. Por isso o
 * conflito é tratado como DADO (linhas afetadas), não como exceção.
 *
 * <h2>Como as duas execuções simultâneas se comportam</h2>
 *
 * <ol>
 *   <li><b>Primeira</b> — o {@code INSERT} insere a linha (1 linha afetada) e segue para
 *       criar Reservation/Appointment. A linha é invisível às outras até o commit.</li>
 *   <li><b>Segunda, enquanto a primeira está aberta</b> — o {@code ON CONFLICT} precisa
 *       decidir sobre uma tupla ainda não commitada, então o PostgreSQL BLOQUEIA a
 *       segunda no índice único até a primeira terminar. Não há duplicação nem erro: há
 *       espera. É o comportamento desejado — a segunda não pode decidir antes de saber o
 *       destino da primeira.</li>
 *   <li><b>Depois do commit da primeira</b> — a segunda destrava com 0 linhas afetadas,
 *       relê a linha (READ COMMITTED enxerga o commit alheio no próximo comando) e
 *       devolve o desfecho gravado. Nenhuma escrita de negócio acontece.</li>
 *   <li><b>Se a primeira sofrer ROLLBACK</b> — a linha reivindicada desaparece, a segunda
 *       destrava e o {@code INSERT} dela SUCEDE (1 linha afetada). Ela assume o comando e
 *       executa normalmente. É por isso que a falha técnica não pode gravar recibo: se
 *       gravasse, o retry veria um comando "concluído" que nunca produziu agendamento.</li>
 * </ol>
 *
 * <h2>Regra do MVP: uma base, um agendamento</h2>
 * A checagem sequencial é um SELECT antes da reivindicação (resposta limpa ao cliente).
 * Sob concorrência, dois comandos distintos da mesma base podem passar por esse SELECT ao
 * mesmo tempo — quem fecha a corrida é o índice parcial
 * {@code uq_booking_idempotency_base_confirmada}: o segundo {@code UPDATE} para CONFIRMADO
 * viola a unicidade, a transação aborta e sobe como falha técnica. Nenhuma tentativa de
 * recuperação dentro da transação já invalidada.
 *
 * <h2>Divergência de fingerprint</h2>
 * A chave já embute o fingerprint, então um acerto com fingerprint diferente significa
 * colisão de SHA-256 ou corrupção. Falha alto — devolver o agendamento de outro comando
 * seria pior do que um erro.
 */
@Component
public class JpaBookingIdempotencyStore implements BookingIdempotencyStore {

    /**
     * Dialeto comum a PostgreSQL e H2 2.x (este último em MODE=PostgreSQL). Mantido como
     * SQL nativo de propósito: o comportamento atômico depende do {@code ON CONFLICT}, que
     * o JPA não expressa.
     *
     * Sem alvo de conflito: o H2 não aceita {@code ON CONFLICT (coluna)}, e a tabela tem
     * uma única constraint — a PK. "Qualquer violação de unicidade" e "violação da PK" são
     * a mesma coisa aqui, então omitir o alvo é equivalente e portável.
     */
    private static final String SQL_REIVINDICAR = """
            INSERT INTO booking_idempotency
                (command_key, business_id, command_base, request_fingerprint, created_at)
            VALUES (:chave, :business, :base, :fingerprint, :agora)
            ON CONFLICT DO NOTHING
            """;

    /**
     * Regra do MVP: uma base conclui no máximo um agendamento — ESCOPADA POR TENANT.
     * Consultado ANTES de reivindicar — se fosse depois, a rejeição deixaria para trás a
     * linha recém-inserida, sem desfecho, e essa chave ficaria presa para sempre.
     *
     * O filtro {@code business_id} é o que impede vazamento entre tenants: dois negócios
     * com a mesma base (cenário adversário) veem regras independentes; a base de um nunca
     * bloqueia o outro. Só CONFIRMADO consome a base.
     */
    private static final String SQL_BASE_CONFIRMADA = """
            SELECT command_key FROM booking_idempotency
             WHERE business_id = :business AND command_base = :base
               AND outcome_status = 'CONFIRMADO'
             LIMIT 1
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Claim reivindicar(BookingCommandKey chave) {
        // Regra do MVP, ANTES de reivindicar: a base já concluiu um agendamento?
        // Se sim e o comando for OUTRO, rejeita sem escrever nada.
        List<?> confirmadaNaBase = entityManager.createNativeQuery(SQL_BASE_CONFIRMADA)
                .setParameter("business", chave.businessId())
                .setParameter("base", chave.base())
                .getResultList();

        if (!confirmadaNaBase.isEmpty()) {
            String chaveConfirmada = String.valueOf(confirmadaNaBase.get(0));
            if (!chaveConfirmada.equals(chave.valor())) {
                BookingIdempotencyJpaEntity anterior =
                        entityManager.find(BookingIdempotencyJpaEntity.class, chaveConfirmada);
                return Claim.baseJaConfirmada(paraRegistro(anterior));
            }
            // Mesma chave: é retry do MESMO comando — segue para o caminho de idempotência.
        }

        int inseridas = entityManager.createNativeQuery(SQL_REIVINDICAR)
                .setParameter("chave", chave.valor())
                .setParameter("business", chave.businessId())
                .setParameter("base", chave.base())
                .setParameter("fingerprint", chave.fingerprint())
                .setParameter("agora", LocalDateTime.now())
                .executeUpdate();

        if (inseridas == 1) {
            return Claim.nova();
        }

        // 0 linhas: a chave já existia e a transação dona já terminou (o INSERT teria
        // bloqueado, caso contrário). Lê o desfecho commitado.
        BookingIdempotencyJpaEntity existente =
                entityManager.find(BookingIdempotencyJpaEntity.class, chave.valor());

        if (existente == null) {
            // Conflito sem linha visível: outra transação inseriu e desfez entre as duas
            // operações. Tratar como "em andamento" — nunca como autorização para gravar.
            return Claim.emAndamento();
        }

        if (!chave.fingerprint().equals(existente.getRequestFingerprint())) {
            throw new IllegalStateException(
                    "Fingerprint divergente para a mesma command key — comando recusado");
        }

        return existente.concluido()
                ? Claim.jaConcluida(paraRegistro(existente))
                : Claim.emAndamento();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void concluir(BookingCommandKey chave, AppointmentId appointmentId,
                         BookingResult.Status status, String servico, String dataIso,
                         String horario, String nome) {
        int atualizadas = entityManager.createNativeQuery("""
                        UPDATE booking_idempotency
                           SET appointment_id  = :appointmentId,
                               outcome_status  = :status,
                               outcome_servico = :servico,
                               outcome_data    = :data,
                               outcome_horario = :horario,
                               outcome_nome    = :nome,
                               completed_at    = :agora
                         WHERE command_key = :chave
                        """)
                .setParameter("appointmentId", appointmentId == null ? null : appointmentId.getValue())
                .setParameter("status", status.name())
                .setParameter("servico", servico)
                .setParameter("data", dataIso)
                .setParameter("horario", horario)
                .setParameter("nome", nome)
                .setParameter("agora", LocalDateTime.now())
                .setParameter("chave", chave.valor())
                .executeUpdate();

        if (atualizadas != 1) {
            // Concluir sem ter reivindicado é erro de programação do caso de uso.
            throw new IllegalStateException("Comando de booking não reivindicado: conclusão recusada");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingIdempotencyRecord> buscar(String commandKey) {
        if (commandKey == null || commandKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(BookingIdempotencyJpaEntity.class, commandKey))
                .map(JpaBookingIdempotencyStore::paraRegistro);
    }

    private static BookingIdempotencyRecord paraRegistro(BookingIdempotencyJpaEntity e) {
        UUID appointmentId = e.getAppointmentId();
        return new BookingIdempotencyRecord(
                e.getCommandKey(),
                e.getRequestFingerprint(),
                appointmentId == null ? Optional.empty() : Optional.of(AppointmentId.from(appointmentId)),
                e.getOutcomeStatus() == null
                        ? BookingResult.Status.FALHA_TECNICA
                        : BookingResult.Status.valueOf(e.getOutcomeStatus()),
                e.getOutcomeServico(), e.getOutcomeData(), e.getOutcomeHorario(), e.getOutcomeNome());
    }
}
