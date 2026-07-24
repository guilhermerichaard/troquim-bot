package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.BookingIds;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GATE MULTI-TENANT — a regra do MVP ("uma base conclui no máximo um agendamento") não
 * pode vazar entre negócios.
 *
 * Cenário adversário: o MESMO {@code flow_token} (base) é apresentado por DOIS negócios
 * distintos. Em produção isso não acontece (o token é 256-bit por sessão, e cada sessão
 * tem um só businessId), mas a GARANTIA do store precisa ser escopada por tenant — se a
 * base de um negócio bloquear o outro, um salão não consegue concluir seu agendamento.
 *
 * O teste dirige o {@link BookingIdempotencyStore} diretamente (via TransactionTemplate,
 * pois as operações são {@code @Transactional(MANDATORY)}), exercitando contra PostgreSQL
 * real TANTO o filtro {@code business_id} do SELECT QUANTO o índice parcial único
 * {@code (business_id, command_base)}. Sem o escopo por tenant, a conclusão do segundo
 * negócio violaria a unicidade e estouraria.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("Idempotência de booking - isolamento entre tenants (PostgreSQL real)")
class BookingIdempotencyMultiTenantPostgresTest {

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

    private static final UUID NEGOCIO_A = TestTenants.PILOT.getValue();
    private static final UUID NEGOCIO_B = TestTenants.OUTRO.getValue();

    @Autowired
    private BookingIdempotencyStore store;

    @Autowired
    private TransactionTemplate tx;

    @Test
    @DisplayName("mesmo flow_token em dois negócios: cada um conclui o seu, sem vazamento")
    void mesmoTokenDoisNegociosNaoVaza() {
        LocalDate dia = LocalDate.now().plusDays(30);
        LocalTime hora = LocalTime.of(9, 0);
        UUID appointmentA = UUID.randomUUID();
        UUID appointmentB = UUID.randomUUID();

        String base = "flow-token-t1";
        BookingCommandKey chaveA = chave(NEGOCIO_A, base, hora, dia);
        BookingCommandKey chaveB = chave(NEGOCIO_B, base, hora, dia);

        // As chaves compartilham a base mas têm command_key distinta (businessId entra no
        // fingerprint) — pré-condição do cenário.
        assertEquals(chaveA.base(), chaveB.base());
        assertFalse(chaveA.valor().equals(chaveB.valor()),
                "businessId no fingerprint torna a command_key distinta por tenant");

        // Negócio A reivindica e conclui.
        BookingIdempotencyStore.Claim claimA = tx.execute(s -> {
            BookingIdempotencyStore.Claim c = store.reivindicar(chaveA);
            store.concluir(chaveA, appointmentIdDe(appointmentA), BookingResult.Status.CONFIRMADO,
                    "unha", dia.toString(), hora.toString(), "Ana A");
            return c;
        });
        assertTrue(claimA.reivindicada(), "A deveria reivindicar do zero");

        // Negócio B, MESMA base: NÃO pode ser barrado pela regra do MVP de A.
        BookingIdempotencyStore.Claim claimB = tx.execute(s -> store.reivindicar(chaveB));
        assertTrue(claimB.reivindicada(),
                "B com a mesma base não pode ser rejeitado por causa do agendamento de A");
        assertTrue(claimB.baseJaConfirmada().isEmpty(),
                "A base de A não pode aparecer como já confirmada para B");

        // E B CONCLUI o seu — o índice parcial (business_id, command_base) permite as duas
        // linhas CONFIRMADO com a mesma base, por serem de tenants diferentes.
        tx.executeWithoutResult(s -> store.concluir(chaveB, appointmentIdDe(appointmentB),
                BookingResult.Status.CONFIRMADO, "unha", dia.toString(), hora.toString(), "Bia B"));

        // Cada recibo aponta para o SEU appointment — nunca o do outro negócio.
        assertEquals(appointmentA, store.buscar(chaveA.valor()).orElseThrow()
                .appointmentId().orElseThrow().getValue());
        assertEquals(appointmentB, store.buscar(chaveB.valor()).orElseThrow()
                .appointmentId().orElseThrow().getValue());
    }

    @Test
    @DisplayName("a regra do MVP continua valendo DENTRO de cada tenant")
    void regraDoMvpValePorTenant() {
        LocalDate dia = LocalDate.now().plusDays(45);
        String base = "flow-token-t2";
        BookingCommandKey primeiro = chave(NEGOCIO_A, base, LocalTime.of(9, 0), dia);
        BookingCommandKey outroComando = chave(NEGOCIO_A, base, LocalTime.of(11, 0), dia);
        assertEquals(primeiro.base(), outroComando.base());

        tx.executeWithoutResult(s -> {
            store.reivindicar(primeiro);
            store.concluir(primeiro, appointmentIdDe(UUID.randomUUID()),
                    BookingResult.Status.CONFIRMADO, "unha", dia.toString(), "09:00", "Ana");
        });

        // MESMO tenant, MESMA base, comando diferente após confirmar → base já confirmada.
        BookingIdempotencyStore.Claim claim = tx.execute(s -> store.reivindicar(outroComando));
        assertFalse(claim.reivindicada(), "Dentro do tenant, a base já foi consumida");
        assertTrue(claim.baseJaConfirmada().isPresent(),
                "A regra do MVP deve barrar um segundo comando da mesma base no mesmo tenant");
    }

    private static BookingCommandKey chave(UUID businessId, String base, LocalTime hora, LocalDate dia) {
        return BookingCommandKey.de(base, businessId, "5511999990000",
                BookingIds.serviceId("unha"), BookingIds.PROFISSIONAL_PADRAO, dia, hora);
    }

    private static com.troquim_bot.appointment.AppointmentId appointmentIdDe(UUID id) {
        return com.troquim_bot.appointment.AppointmentId.from(id);
    }
}
