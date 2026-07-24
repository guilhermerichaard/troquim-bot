package com.troquim_bot.support;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyRecord;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.appointment.AppointmentId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store de idempotência em memória, para os testes de UNIDADE do caso de uso.
 *
 * LIMITE EXPLÍCITO: reproduz a semântica sequencial (reivindicar → concluir → retry lê o
 * desfecho), mas NÃO reproduz o bloqueio no índice único do PostgreSQL nem rollback. Os
 * cenários de concorrência e de rollback só valem no teste de integração com Postgres
 * real — um fake em memória não prova nada sobre commit incerto.
 */
public final class InMemoryBookingIdempotencyStore implements BookingIdempotencyStore {

    private final Map<String, BookingIdempotencyRecord> registros = new ConcurrentHashMap<>();
    private final Map<String, String> reivindicadas = new ConcurrentHashMap<>();
    // Regra do MVP escopada por tenant: chave "businessId|base" → command_key confirmado.
    private final Map<String, String> confirmadaTenantBase = new ConcurrentHashMap<>();

    @Override
    public Claim reivindicar(BookingCommandKey chave) {
        // Regra do MVP: uma base conclui no maximo um agendamento — ESCOPADA POR TENANT.
        // Sem o business_id no filtro, dois negocios com a mesma base vazariam entre si.
        String confirmada = confirmadaTenantBase.get(tenantBase(chave));
        if (confirmada != null && !confirmada.equals(chave.valor())) {
            return Claim.baseJaConfirmada(registros.get(confirmada));
        }

        BookingIdempotencyRecord concluido = registros.get(chave.valor());
        if (concluido != null) {
            if (!chave.fingerprint().equals(concluido.fingerprint())) {
                throw new IllegalStateException("Fingerprint divergente para a mesma command key");
            }
            return Claim.jaConcluida(concluido);
        }
        // putIfAbsent: só o primeiro a chegar reivindica.
        return reivindicadas.putIfAbsent(chave.valor(), chave.fingerprint()) == null
                ? Claim.nova()
                : Claim.emAndamento();
    }

    @Override
    public void concluir(BookingCommandKey chave, AppointmentId appointmentId,
                         BookingResult.Status status, String servico, String dataIso,
                         String horario, String nome) {
        if (!reivindicadas.containsKey(chave.valor())) {
            throw new IllegalStateException("Comando de booking não reivindicado: conclusão recusada");
        }
        registros.put(chave.valor(), new BookingIdempotencyRecord(
                chave.valor(), chave.fingerprint(), Optional.ofNullable(appointmentId),
                status, servico, dataIso, horario, nome));
        if (status == BookingResult.Status.CONFIRMADO) {
            confirmadaTenantBase.put(tenantBase(chave), chave.valor());
        }
    }

    private static String tenantBase(BookingCommandKey chave) {
        return chave.businessId() + "|" + chave.base();
    }

    @Override
    public Optional<BookingIdempotencyRecord> buscar(String commandKey) {
        return Optional.ofNullable(registros.get(commandKey));
    }

    /**
     * Simula o rollback da transação: apaga a reivindicação não concluída, como o banco
     * faria. Sem isto, um retry após falha técnica ficaria preso em "em andamento".
     */
    public void desfazerReivindicacaoNaoConcluida(BookingCommandKey chave) {
        if (!registros.containsKey(chave.valor())) {
            reivindicadas.remove(chave.valor());
        }
    }

    public int totalConcluidos() {
        return registros.size();
    }
}
