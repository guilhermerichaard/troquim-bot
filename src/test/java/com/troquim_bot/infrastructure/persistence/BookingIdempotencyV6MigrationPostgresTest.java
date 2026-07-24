package com.troquim_bot.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GATE DA MIGRATION V6 — prova que o upgrade V5→V6 funciona em banco NÃO VAZIO.
 *
 * Diferente dos demais testes (que sobem V1→V6 num banco limpo pelo Spring), este dirige
 * o Flyway diretamente em DUAS etapas contra um PostgreSQL real:
 * <ol>
 *   <li>migra V1→V5;</li>
 *   <li>insere registros REALISTAS via JDBC, incluindo a relação canônica
 *       (customers → appointments → booking_idempotency) e um registro não-confirmado
 *       (INDISPONIVEL, appointment_id NULL) que persiste;</li>
 *   <li>migra V6;</li>
 *   <li>verifica backfill canônico, CHECK, índice, isolamento entre tenants, retry
 *       pós-migration e ausência de perda de dados.</li>
 * </ol>
 *
 * business_id é derivado da relação canônica (appointment→customer), NUNCA de hash nem de
 * tenant fictício. Requer Docker; sem ele o teste é ignorado.
 */
@DisplayName("Migration V6 - upgrade em banco não vazio (PostgreSQL real)")
class BookingIdempotencyV6MigrationPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Bases DISTINTAS por tenant — o estado legado real: cada sessão tem seu flow_token.
    // (Sob a V5, dois CONFIRMADO não podem compartilhar base; é o próprio bug.)
    private static final String BASE_A = "flow-token-a";
    private static final String BASE_B = "flow-token-b";
    private static final String BASE_INDISP = "flow-token-c";

    private static PostgreSQLContainer<?> pg;

    @BeforeAll
    static void up() {
        assumeTrue(dockerDisponivel(), "Docker indisponível — teste de migration ignorado");
        pg = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("troquim").withUsername("troquim").withPassword("troquim-test");
        pg.start();
    }

    @AfterAll
    static void down() {
        if (pg != null) {
            pg.stop();
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("pilot_business_id", TENANT_A.toString()))
                .target(target)
                .load();
    }

    @Test
    @DisplayName("V5→V6 preenche business_id pela relação canônica, sem perder dados")
    void upgradeComDados() throws Exception {
        // 1. Migra até V5 (schema anterior ao business_id).
        flyway("5").migrate();

        LocalDate dia = LocalDate.now().plusDays(30);
        UUID apptA = UUID.randomUUID();
        UUID apptB = UUID.randomUUID();
        UUID custA = UUID.randomUUID();
        UUID custB = UUID.randomUUID();

        try (Connection c = conn()) {
            c.setAutoCommit(true);
            // Estado legado realista: cada negócio com cliente + appointment + recibo
            // CONFIRMADO, cada um com sua PRÓPRIA base (como sessões reais).
            inserirCliente(c, custA, TENANT_A, "+5511999990001");
            inserirCliente(c, custB, TENANT_B, "+5511999990002");
            inserirAppointment(c, apptA, custA, dia, "09:00");
            inserirAppointment(c, apptB, custB, dia, "10:00");
            inserirRecibo(c, BASE_A, apptA, "CONFIRMADO", "Ana A");
            inserirRecibo(c, BASE_B, apptB, "CONFIRMADO", "Bia B");
            // Registro não-confirmado que PERSISTE: conflito, sem appointment → sem relação
            // canônica. Deve sobreviver à V6 com business_id NULL (nunca é consultado).
            inserirRecibo(c, BASE_INDISP, null, "INDISPONIVEL", null);
        }

        assertEquals(3, contar("SELECT count(*) FROM booking_idempotency"),
                "Pré-condição: 3 registros antes da V6");

        // 2. Migra V6.
        flyway("6").migrate();

        // 3a. Backfill canônico correto por tenant (via appointment→customer).
        assertEquals(TENANT_A, tenantDoRecibo(apptA), "CONFIRMADO de A backfillado com TENANT_A");
        assertEquals(TENANT_B, tenantDoRecibo(apptB), "CONFIRMADO de B backfillado com TENANT_B");

        // 3b. Não-confirmado permanece NULL e NÃO foi perdido.
        assertNull(tenantDoReciboPorBase(BASE_INDISP),
                "INDISPONIVEL sem appointment permanece com business_id NULL");
        assertEquals(3, contar("SELECT count(*) FROM booking_idempotency"),
                "Nenhum registro pode ter sido perdido na migration");

        // 4. Estrutura final: índice novo presente, antigo removido, CHECK presente.
        assertTrue(indiceExiste("uq_booking_idempotency_tenant_base_confirmada"), "índice novo");
        assertFalse(indiceExiste("uq_booking_idempotency_base_confirmada"), "índice antigo removido");
        assertTrue(constraintExiste("chk_booking_idem_confirmada_tenant"), "CHECK de tenant");

        try (Connection c = conn()) {
            c.setAutoCommit(true);

            // 5. Retry/regra pós-migration: um SEGUNDO CONFIRMADO para (TENANT_A, BASE_A)
            // é BLOQUEADO pelo índice re-escopado — a regra do MVP vale por tenant após V6.
            UUID apptA2 = UUID.randomUUID();
            UUID custA2 = UUID.randomUUID();
            inserirCliente(c, custA2, TENANT_A, "+5511999990003");
            inserirAppointment(c, apptA2, custA2, dia, "11:00");
            assertThrows(SQLException.class, () -> inserirReciboComTenant(
                    c, BASE_A, TENANT_A, apptA2, "CONFIRMADO", "Ana A2"),
                    "Segundo CONFIRMADO da mesma base+tenant deve violar o índice único");

            // 6. Isolamento pós-migration: OUTRO tenant pode confirmar com a MESMA base de A
            // — o que era IMPOSSÍVEL sob a V5. Prova direta de que o vazamento foi fechado.
            UUID apptB2 = UUID.randomUUID();
            UUID custB2 = UUID.randomUUID();
            inserirCliente(c, custB2, TENANT_B, "+5511999990004");
            inserirAppointment(c, apptB2, custB2, dia, "12:00");
            inserirReciboComTenant(c, BASE_A, TENANT_B, apptB2, "CONFIRMADO", "Bia B2");
            assertEquals(2, contar(
                    "SELECT count(*) FROM booking_idempotency WHERE command_base = '" + BASE_A
                            + "' AND outcome_status = 'CONFIRMADO'"),
                    "Dois tenants distintos podem confirmar a mesma base após a V6");
        }
    }

    // ==================== inserts realistas ====================

    private void inserirCliente(Connection c, UUID id, UUID tenant, String phone) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO customers (id, first_name, last_name, phone, status,
                    total_atendimentos, criado_em, atualizado_em, business_id, phone_e164)
                VALUES (?, 'Cli', 'Ente', ?, 'ATIVO', 0, now(), now(), ?, ?)""")) {
            ps.setObject(1, id);
            ps.setString(2, phone.substring(1));
            ps.setObject(3, tenant);
            ps.setString(4, phone);
            ps.executeUpdate();
        }
    }

    private void inserirAppointment(Connection c, UUID id, UUID customerId, LocalDate dia,
                                    String hora) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO appointments (id, customer_id, professional_id, service_id,
                    availability_id, date, start_time, end_time, status, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?::time, (?::time + interval '1 hour'),
                    'CONFIRMADO', now(), now())""")) {
            ps.setObject(1, id);
            ps.setObject(2, customerId);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, UUID.randomUUID());
            ps.setObject(5, UUID.randomUUID());
            ps.setObject(6, dia);
            ps.setString(7, hora);
            ps.setString(8, hora);
            ps.executeUpdate();
        }
    }

    /** Recibo no schema V5 (SEM business_id). */
    private void inserirRecibo(Connection c, String base, UUID appointmentId, String status,
                               String nome) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO booking_idempotency (command_key, command_base, request_fingerprint,
                    appointment_id, outcome_status, outcome_nome, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, now(), now())""")) {
            ps.setString(1, base + ":" + UUID.randomUUID());
            ps.setString(2, base);
            ps.setString(3, "0".repeat(64));
            ps.setObject(4, appointmentId);
            ps.setString(5, status);
            ps.setString(6, nome);
            ps.executeUpdate();
        }
    }

    /** Recibo no schema V6 (COM business_id) — para o teste de retry pós-migration. */
    private void inserirReciboComTenant(Connection c, String base, UUID tenant, UUID appointmentId,
                                        String status, String nome) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO booking_idempotency (command_key, business_id, command_base,
                    request_fingerprint, appointment_id, outcome_status, outcome_nome,
                    created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())""")) {
            ps.setString(1, base + ":" + UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setString(3, base);
            ps.setString(4, "1".repeat(64));
            ps.setObject(5, appointmentId);
            ps.setString(6, status);
            ps.setString(7, nome);
            ps.executeUpdate();
        }
    }

    // ==================== leitura ====================

    private UUID tenantDoRecibo(UUID appointmentId) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT business_id FROM booking_idempotency WHERE appointment_id = '"
                             + appointmentId + "'")) {
            assertTrue(rs.next());
            return rs.getObject(1, UUID.class);
        }
    }

    private UUID tenantDoReciboPorBase(String base) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT business_id FROM booking_idempotency WHERE command_base = '"
                             + base + "'")) {
            assertTrue(rs.next());
            return rs.getObject(1, UUID.class);
        }
    }

    private long contar(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean indiceExiste(String nome) throws SQLException {
        return contar("SELECT count(*) FROM pg_indexes WHERE indexname = '" + nome + "'") == 1;
    }

    private boolean constraintExiste(String nome) throws SQLException {
        return contar("SELECT count(*) FROM pg_constraint WHERE conname = '" + nome + "'") == 1;
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private static boolean dockerDisponivel() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
