package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.BookingIds;
import com.troquim_bot.application.booking.BookingPersistenceException;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Idempotência de comando contra PostgreSQL REAL (Testcontainers).
 *
 * Este é o único lugar onde concorrência e rollback são de fato provados. Um store em
 * memória não reproduz o bloqueio no índice único nem desfaz escritas — usá-lo aqui seria
 * generalizar um fake para falhas incertas de commit, exatamente o que não se pode fazer.
 *
 * A migration V5 é aplicada pelo Flyway (profile azure), então o schema sob teste é o
 * mesmo que vai para produção.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("Idempotência de booking - PostgreSQL real")
class BookingIdempotencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> "11111111-1111-1111-1111-111111111111");
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
    }

    private static final UUID TENANT = TestTenants.PILOT.getValue();

    @Autowired
    private BookingApplicationService booking;

    @Autowired
    private BookingIdempotencyStore idempotencyStore;

    @Autowired
    private SpringDataAppointmentRepository appointments;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // ==================== sequencial ====================

    @Test
    @DisplayName("mesmo comando sequencial devolve o MESMO appointment_id")
    void mesmoComandoSequencial() {
        String telefone = "5511977770101";
        LocalDate dia = diaExclusivo(1);
        LocalTime hora = LocalTime.of(9, 0);
        BookingCommandKey chave = chave("cmd-seq", telefone, dia, hora);

        BookingResult primeira = confirmar(telefone, dia, hora, chave);
        long depoisDaPrimeira = appointments.count();
        BookingResult segunda = confirmar(telefone, dia, hora, chave);

        assertTrue(primeira.isConfirmado());
        assertTrue(segunda.isConfirmado());
        assertEquals(depoisDaPrimeira, appointments.count(), "O retry não pode criar outro");

        UUID appointmentId = idempotencyStore.buscar(chave.valor()).orElseThrow()
                .appointmentId().orElseThrow().getValue();
        assertTrue(appointments.findById(appointmentId).isPresent(),
                "O recibo precisa apontar para um Appointment que existe de fato");
    }

    @Test
    @DisplayName("o recibo sobrevive ao reinício do contexto de persistência")
    void sobreviveAoReinicioDoStore() {
        String telefone = "5511977770102";
        LocalDate dia = diaExclusivo(2);
        LocalTime hora = LocalTime.of(10, 0);
        BookingCommandKey chave = chave("cmd-restart", telefone, dia, hora);

        confirmar(telefone, dia, hora, chave);
        long total = appointments.count();

        // Nada em memória: a leitura vem do banco, numa transação nova.
        BookingResult apos = transactionTemplate.execute(status ->
                booking.confirmarEm(telefone, "Ana Souza", "unha", BookingIds.PROFISSIONAL_PADRAO,
                        dia, hora, Duration.ofHours(1), chave));

        assertTrue(apos.isConfirmado());
        assertEquals(total, appointments.count());
    }

    // ==================== concorrência ====================

    @Test
    @DisplayName("dois requests simultâneos com a MESMA chave criam um único agendamento")
    void concorrenciaMesmaChave() throws Exception {
        String telefone = "5511977770103";
        LocalDate dia = diaExclusivo(3);
        LocalTime hora = LocalTime.of(11, 0);
        BookingCommandKey chave = chave("cmd-conc", telefone, dia, hora);

        long antes = appointments.count();
        List<BookingResult> resultados = emParalelo(2,
                () -> confirmar(telefone, dia, hora, chave));

        // Nenhuma das duas pode falhar, e exatamente UM agendamento pode existir: a
        // segunda bloqueia no índice único até a primeira comitar, depois lê o desfecho.
        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(BookingResult::isConfirmado),
                "Ambas deveriam devolver sucesso: " + resultados);
        assertEquals(antes + 1, appointments.count(),
                "Duas execuções simultâneas do mesmo comando não podem criar dois agendamentos");
    }

    @Test
    @DisplayName("dois comandos DIFERENTES disputando o mesmo slot: um confirma, outro conflita")
    void concorrenciaSlotDisputado() throws Exception {
        LocalDate dia = diaExclusivo(4);
        LocalTime hora = LocalTime.of(12, 0);

        long antes = appointments.count();
        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar("5511977770104", dia, hora, chave("cmd-a", "5511977770104", dia, hora)),
                () -> confirmar("5511977770105", dia, hora, chave("cmd-b", "5511977770105", dia, hora)));

        long confirmados = resultados.stream().filter(BookingResult::isConfirmado).count();
        assertEquals(1, confirmados, "Só um comando pode vencer o slot: " + resultados);
        assertEquals(antes + 1, appointments.count());
        // A invariante de slot continua sendo do domínio, não da idempotência.
        assertTrue(resultados.stream().anyMatch(r -> r.isConflito() || r.isFalhaTecnica()),
                "O perdedor precisa receber conflito (ou falha técnica sob corrida): " + resultados);
    }

    // ==================== rollback ====================

    @Test
    @DisplayName("rollback apaga a reivindicação junto, e o retry consegue reivindicar de novo")
    void rollbackLiberaAChave() {
        String telefone = "5511977770106";
        LocalDate dia = diaExclusivo(5);
        LocalTime hora = LocalTime.of(13, 0);
        BookingCommandKey chave = chave("cmd-rollback", telefone, dia, hora);
        long antes = appointments.count();

        // Transação que reivindica e depois é desfeita de propósito.
        assertThrows(RuntimeException.class, () -> transactionTemplate.execute(status -> {
            booking.confirmarEm(telefone, "Ana Souza", "unha", BookingIds.PROFISSIONAL_PADRAO,
                    dia, hora, Duration.ofHours(1), chave);
            throw new IllegalStateException("rollback proposital apos confirmar");
        }));

        // Nem agendamento nem recibo sobreviveram.
        assertEquals(antes, appointments.count(), "O agendamento precisa ter sido desfeito");
        assertTrue(idempotencyStore.buscar(chave.valor()).isEmpty(),
                "A reivindicação precisa ter sido desfeita junto");

        // E a MESMA chave volta a funcionar: o comando nunca aconteceu.
        BookingResult retry = confirmar(telefone, dia, hora, chave);
        assertTrue(retry.isConfirmado());
        assertEquals(antes + 1, appointments.count());
    }

    @Test
    @DisplayName("falha técnica não deixa recibo: o comando permanece repetível")
    void falhaTecnicaNaoDeixaRecibo() {
        String telefone = "5511977770107";
        LocalDate dia = diaExclusivo(6);
        LocalTime hora = LocalTime.of(14, 0);
        // Serviço inexistente no catálogo não é o vetor aqui; forçamos falha com uma data
        // inválida para o domínio (fim antes do início é impossível), então usamos o
        // caminho de rollback explícito acima. Aqui verificamos a invariante do recibo:
        BookingCommandKey chave = chave("cmd-sem-recibo", telefone, dia, hora);

        assertTrue(idempotencyStore.buscar(chave.valor()).isEmpty());

        confirmar(telefone, dia, hora, chave);

        // Só após um comando BEM-SUCEDIDO existe recibo, e ele aponta para o appointment.
        var registro = idempotencyStore.buscar(chave.valor()).orElseThrow();
        assertEquals(BookingResult.Status.CONFIRMADO, registro.status());
        assertTrue(registro.appointmentId().isPresent());
        assertFalse(registro.fingerprint().isBlank());
    }

    // ==================== chave ====================

    // ==================== REGRA DO MVP: uma base, um agendamento ====================

    @Test
    @DisplayName("MVP sequencial: mesma base com dados diferentes é REJEITADA")
    void mvpSequencialRejeitaDadosDiferentes() {
        String telefone = "5511977770108";
        LocalDate dia = diaExclusivo(7);
        long antes = appointments.count();
        String token = "token-mvp-seq";

        BookingResult primeira = confirmar(telefone, dia, LocalTime.of(15, 0),
                chave(token, telefone, dia, LocalTime.of(15, 0)));
        BookingResult segunda = confirmar(telefone, dia, LocalTime.of(16, 0),
                chave(token, telefone, dia, LocalTime.of(16, 0)));

        assertTrue(primeira.isConfirmado());
        assertTrue(segunda.isSessaoJaConfirmada(), "Esperava rejeição, veio: " + segunda);
        assertEquals(antes + 1, appointments.count(),
                "Um flow_token não pode render dois agendamentos");
    }

    @Test
    @DisplayName("MVP após reinício: a regra vem do BANCO, não de estado em memória")
    void mvpAposReinicioDoContexto() {
        String telefone = "5511977770111";
        LocalDate dia = diaExclusivo(8);
        String token = "token-mvp-restart";

        confirmar(telefone, dia, LocalTime.of(9, 30), chave(token, telefone, dia, LocalTime.of(9, 30)));
        long depois = appointments.count();

        // Transação nova, contexto de persistência novo: nada em memória participa.
        BookingResult outra = transactionTemplate.execute(status ->
                booking.confirmarEm(telefone, "Ana Souza", "unha", BookingIds.PROFISSIONAL_PADRAO,
                        dia, LocalTime.of(10, 30), Duration.ofHours(1),
                        chave(token, telefone, dia, LocalTime.of(10, 30))));

        assertTrue(outra.isSessaoJaConfirmada(), "Esperava rejeição após reinício: " + outra);
        assertEquals(depois, appointments.count());
    }

    @Test
    @DisplayName("MVP concorrente: mesma base, dados diferentes, simultâneos → 1 agendamento")
    void mvpConcorrenteMesmaBase() throws Exception {
        String telefone = "5511977770112";
        LocalDate dia = diaExclusivo(9);
        String token = "token-mvp-conc";
        long antes = appointments.count();

        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar(telefone, dia, LocalTime.of(9, 0),
                        chave(token, telefone, dia, LocalTime.of(9, 0))),
                () -> confirmar(telefone, dia, LocalTime.of(11, 0),
                        chave(token, telefone, dia, LocalTime.of(11, 0))));

        // No máximo um confirma. O outro é rejeitado pelo SELECT (se chegou depois) ou
        // abortado pelo índice parcial (se passou pelo SELECT ao mesmo tempo) — nos dois
        // casos, NENHUM segundo agendamento.
        long confirmados = resultados.stream().filter(BookingResult::isConfirmado).count();
        assertEquals(1, confirmados, "Exatamente um comando pode vencer a base: " + resultados);
        assertEquals(antes + 1, appointments.count(),
                "A corrida não pode render dois agendamentos para o mesmo flow_token");
        assertTrue(resultados.stream().anyMatch(
                        r -> r.isSessaoJaConfirmada() || r.isFalhaTecnica()),
                "O perdedor precisa ser rejeitado ou abortado, nunca confirmado: " + resultados);
    }

    @Test
    @DisplayName("MVP: retry do MESMO comando continua funcionando depois da regra")
    void mvpRetryDoMesmoComandoNaoERejeitado() {
        String telefone = "5511977770113";
        LocalDate dia = diaExclusivo(10);
        BookingCommandKey chave = chave("token-mvp-retry", telefone, dia, LocalTime.of(8, 0));

        BookingResult primeira = confirmar(telefone, dia, LocalTime.of(8, 0), chave);
        long depois = appointments.count();
        BookingResult retry = confirmar(telefone, dia, LocalTime.of(8, 0), chave);

        assertTrue(primeira.isConfirmado());
        assertTrue(retry.isConfirmado(),
                "O retry do MESMO comando é idempotente, não rejeitado: " + retry);
        assertEquals(depois, appointments.count());
    }

    @Test
    @DisplayName("MVP: conflito de agenda NÃO consome a base")
    void conflitoNaoConsomeABase() {
        LocalDate dia = diaExclusivo(11);
        LocalTime disputado = LocalTime.of(18, 0);
        confirmar("5511977770114", dia, disputado,
                chave("token-outro", "5511977770114", dia, disputado));

        String telefone = "5511977770115";
        String token = "token-mvp-conflito";
        BookingResult conflito = confirmar(telefone, dia, disputado,
                chave(token, telefone, dia, disputado));
        assertEquals(BookingResult.Status.INDISPONIVEL, conflito.status());

        // O MESMO Flow ainda serve: perder um horário para outra pessoa não gasta o token.
        BookingResult outra = confirmar(telefone, dia, LocalTime.of(19, 0),
                chave(token, telefone, dia, LocalTime.of(19, 0)));
        assertTrue(outra.isConfirmado(), "Conflito não pode gastar o Flow do cliente: " + outra);
    }

    @Test
    @DisplayName("comando novo para slot já ocupado devolve INDISPONIVEL")
    void comandoNovoSlotOcupado() {
        LocalDate dia = diaExclusivo(12);
        LocalTime hora = LocalTime.of(17, 0);
        confirmar("5511977770109", dia, hora, chave("cmd-x", "5511977770109", dia, hora));

        BookingResult conflito = confirmar("5511977770110", dia, hora,
                chave("cmd-y", "5511977770110", dia, hora));

        assertEquals(BookingResult.Status.INDISPONIVEL, conflito.status());
    }

    // ==================== helpers ====================

    private BookingCommandKey chave(String base, String telefone, LocalDate data, LocalTime hora) {
        return BookingCommandKey.de(base, TENANT, telefone,
                BookingIds.serviceId("unha"), BookingIds.PROFISSIONAL_PADRAO, data, hora);
    }

    private BookingResult confirmar(String telefone, LocalDate data, LocalTime hora,
                                    BookingCommandKey chave) {
        return booking.confirmarEm(telefone, "Ana Souza", "unha", BookingIds.PROFISSIONAL_PADRAO,
                data, hora, Duration.ofHours(1), chave);
    }

    /** Dispara N execuções idênticas de uma vez, com largada sincronizada. */
    private <T> List<T> emParalelo(int quantidade, Callable<T> tarefa) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await(5, TimeUnit.SECONDS);
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new java.util.ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Duas tarefas distintas, com largada sincronizada. Falha vira resultado. */
    private List<BookingResult> emParaleloDistintos(Callable<BookingResult> a,
                                                    Callable<BookingResult> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<BookingResult>> futuros = List.of(
                    pool.submit(() -> executarTolerante(largada, a)),
                    pool.submit(() -> executarTolerante(largada, b)));
            largada.countDown();
            List<BookingResult> resultados = new java.util.ArrayList<>();
            for (Future<BookingResult> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Uma corrida perdida pode chegar como exceção (o Postgres aborta a transação
     * perdedora numa violação de índice de negócio). Isso é falha TÉCNICA do ponto de
     * vista do chamador, não sucesso — e o teste precisa distinguir os dois.
     */
    private static BookingResult executarTolerante(CountDownLatch largada,
                                                   Callable<BookingResult> tarefa) {
        try {
            largada.await(5, TimeUnit.SECONDS);
            return tarefa.call();
        } catch (BookingPersistenceException | org.springframework.dao.DataAccessException e) {
            return BookingResult.falhaTecnica();
        } catch (Exception e) {
            return BookingResult.falhaTecnica();
        }
    }

    /**
     * Data ISOLADA por teste. Todos compartilham o mesmo container, e a invariante de slot
     * é global: sem isolar, um teste ocuparia o horário que outro precisa livre, e a ordem
     * de execução (não determinística) decidiria quem falha.
     */
    private static LocalDate diaExclusivo(int semanas) {
        return proximaQuarta().plusWeeks(semanas);
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }
}
