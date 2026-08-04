package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingCommandKeyReutilizadaException;
import com.troquim_bot.application.booking.BookingIdempotencyOutcome;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.RegistrarDesfechoDeBookingSemAgendamento;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.service.ServiceId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concorrência REAL (PostgreSQL/Testcontainers) do registro de recibo SEM agendamento
 * ({@link RegistrarDesfechoDeBookingSemAgendamento}), usada pela API pública quando o
 * catálogo recusa a seleção (SELECAO_INDISPONIVEL).
 *
 * Prova a lacuna fechada pela Etapa 5D-B: duas requisições concorrentes sob a MESMA
 * {@code Idempotency-Key} externa, com PAYLOADS diferentes (fingerprints diferentes), não
 * podem as duas vincular a chave — o mecanismo atômico é o MESMO usado por
 * {@code BookingApplicationService} ({@code INSERT ... ON CONFLICT} + comparação de
 * fingerprint em {@code JpaBookingIdempotencyStore}), nenhuma tabela ou lock novo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("Registro de recibo sem agendamento - concorrência real (PostgreSQL)")
class RegistrarDesfechoDeBookingSemAgendamentoPostgresTest {

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

    @Autowired
    private RegistrarDesfechoDeBookingSemAgendamento registrar;
    @Autowired
    private BookingIdempotencyStore idempotencyStore;
    @Autowired
    private BusinessRepository businessRepository;

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private static BookingCommandKey chave(BusinessId businessId, String idempotencyKeyExterna,
                                           String telefone, LocalTime horario) {
        return BookingCommandKey.deChaveExclusiva(businessId, idempotencyKeyExterna, telefone,
                ServiceId.from(UUID.randomUUID()), ProfessionalId.from(UUID.randomUUID()),
                proximaQuarta(), horario);
    }

    @Test
    @DisplayName("mesma Idempotency-Key, dois payloads (fingerprints) concorrentes: "
            + "um vincula, o outro é reutilização; um único recibo")
    void doisFingerprintsConcorrentesMesmaChaveApenasUmVincula() throws Exception {
        BusinessId businessId = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(businessId, "Negocio Concorrencia", null, null));
        String idempotencyKeyExterna = "conc-key-" + UUID.randomUUID();

        BookingCommandKey chaveA = chave(businessId, idempotencyKeyExterna, "+5511977000001",
                LocalTime.of(10, 0));
        BookingCommandKey chaveB = chave(businessId, idempotencyKeyExterna, "+5511977000002",
                LocalTime.of(11, 0));

        assertEquals(chaveA.valor(), chaveB.valor(),
                "mesma (businessId, Idempotency-Key) tem de produzir o MESMO command_key");
        assertTrue(!chaveA.fingerprint().equals(chaveB.fingerprint()),
                "payloads diferentes têm de produzir fingerprints diferentes");

        List<Resultado> resultados = emParaleloDistintos(
                () -> tentar(chaveA),
                () -> tentar(chaveB));

        long vinculou = resultados.stream().filter(r -> r.outcome != null).count();
        long reutilizou = resultados.stream().filter(r -> r.reuso).count();

        assertEquals(1, vinculou, "Exatamente uma tentativa pode vincular a chave: " + resultados);
        assertEquals(1, reutilizou, "A outra tem de ser reutilização detectada: " + resultados);

        var recibo = idempotencyStore.buscar(chaveA.valor()).orElseThrow();
        assertEquals(BookingIdempotencyOutcome.SELECAO_INDISPONIVEL, recibo.status());
        assertTrue(recibo.appointmentId().isEmpty(), "Este caminho nunca cria Appointment");
    }

    private Resultado tentar(BookingCommandKey chave) {
        try {
            BookingIdempotencyOutcome outcome = registrar.registrarSelecaoIndisponivel(chave);
            return new Resultado(outcome, false);
        } catch (BookingCommandKeyReutilizadaException e) {
            return new Resultado(null, true);
        }
    }

    private record Resultado(BookingIdempotencyOutcome outcome, boolean reuso) {
    }

    /** Duas tarefas distintas, largada sincronizada. */
    private List<Resultado> emParaleloDistintos(Callable<Resultado> a, Callable<Resultado> b)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Resultado>> futuros = List.of(
                    pool.submit(() -> executarComLargada(largada, a)),
                    pool.submit(() -> executarComLargada(largada, b)));
            largada.countDown();
            List<Resultado> resultados = new java.util.ArrayList<>();
            for (Future<Resultado> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Resultado executarComLargada(CountDownLatch largada, Callable<Resultado> tarefa)
            throws Exception {
        largada.await(5, TimeUnit.SECONDS);
        return tarefa.call();
    }
}
