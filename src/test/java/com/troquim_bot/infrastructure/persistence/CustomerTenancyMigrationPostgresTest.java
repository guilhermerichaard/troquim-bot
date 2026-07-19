package com.troquim_bot.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida as migrations Flyway e as constraints REAIS no PostgreSQL — H2 não é
 * evidência suficiente. Cobre: aplicar em banco vazio (8), aplicar sobre dados
 * legados válidos com backfill (9), falhar com diagnóstico em duplicatas (10),
 * a UNIQUE (business_id, phone_e164) real (7) e o NOT NULL de business_id (6).
 *
 * Cada teste parte de um schema limpo (Flyway clean no @BeforeEach).
 */
@Testcontainers(disabledWithoutDocker = true)
class CustomerTenancyMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    private static final String PILOT = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void limparSchema() {
        flyway(null).clean();
    }

    // ==================== #8 — banco vazio ====================

    @Test
    void migrationAplicaEmBancoVazio() throws SQLException {
        flyway(null).migrate();
        assertTrue(uniqueConstraintExiste(),
                "UNIQUE (business_id, phone_e164) deve existir após migrar em banco vazio");
    }

    // ==================== #9 — dados legados válidos ====================

    @Test
    void migrationAplicaSobreDadosLegadosValidosComBackfill() throws SQLException {
        flyway("1").migrate();
        exec(inserirCustomerLegado("aaaaaaaa-0000-0000-0000-000000000001", "5511999990001"));

        flyway(null).migrate(); // aplica V2

        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT business_id, phone_e164 FROM customers")) {
            assertTrue(rs.next(), "o cliente legado deve continuar existindo");
            assertEquals(PILOT, rs.getString("business_id"), "business_id deve receber o backfill do piloto");
            assertEquals("+5511999990001", rs.getString("phone_e164"), "phone_e164 deve ser canônico E.164");
        }
    }

    // ==================== #10 — duplicatas fazem a migration falhar ====================

    @Test
    void migrationFalhaComDiagnosticoEmDuplicatas() {
        flyway("1").migrate();
        assertDoesNotThrow(() -> {
            exec(inserirCustomerLegado("aaaaaaaa-0000-0000-0000-000000000001", "5511999990001"));
            // mesmo telefone → mesmo phone_e164 após backfill → duplicata no mesmo tenant
            exec(inserirCustomerLegado("aaaaaaaa-0000-0000-0000-000000000002", "5511999990001"));
        });

        FlywayException ex = assertThrows(FlywayException.class, () -> flyway(null).migrate());
        String texto = (ex.getMessage() + " " + causaMensagem(ex)).toLowerCase();
        assertTrue(texto.contains("duplicad"),
                "A migration deve falhar com diagnóstico de duplicatas. Mensagem: " + texto);
    }

    // ==================== #7 — UNIQUE real ====================

    @Test
    void uniqueConstraintRealNoPostgres() throws SQLException {
        flyway(null).migrate();
        exec(inserirCustomerV2("bbbbbbbb-0000-0000-0000-000000000001", "+5511999990001"));

        SQLException ex = assertThrows(SQLException.class, () ->
                exec(inserirCustomerV2("bbbbbbbb-0000-0000-0000-000000000002", "+5511999990001")));
        assertEquals("23505", ex.getSQLState(),
                "Segundo insert com mesma (business_id, phone_e164) deve violar a UNIQUE (23505)");
    }

    // ==================== #6 — business_id NOT NULL real ====================

    @Test
    void persistenciaSemBusinessIdViolaNotNull() throws SQLException {
        flyway(null).migrate();

        SQLException ex = assertThrows(SQLException.class, () -> exec(
                "INSERT INTO customers (id, phone_e164, first_name, last_name, phone, status, "
                        + "total_atendimentos, criado_em, atualizado_em) VALUES "
                        + "('cccccccc-0000-0000-0000-000000000001', '+5511999990009', 'Ana', 'Paula', "
                        + "'5511999990009', 'ATIVO', 0, now(), now())"));
        assertEquals("23502", ex.getSQLState(), "business_id NULL deve violar NOT NULL (23502)");
    }

    // ==================== helpers ====================

    private Flyway flyway(String target) {
        var cfg = Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("pilot_business_id", PILOT))
                .cleanDisabled(false);
        if (target != null) {
            cfg = cfg.target(target);
        }
        return cfg.load();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private boolean uniqueConstraintExiste() throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT 1 FROM information_schema.table_constraints "
                             + "WHERE table_name = 'customers' AND constraint_type = 'UNIQUE' "
                             + "AND constraint_name = 'uq_customers_business_phone'")) {
            return rs.next();
        }
    }

    private String inserirCustomerLegado(String id, String phone) {
        // Schema V1: customers ainda sem business_id/phone_e164.
        return "INSERT INTO customers (id, first_name, last_name, phone, status, total_atendimentos, "
                + "criado_em, atualizado_em) VALUES "
                + "('" + id + "', 'Ana', 'Paula', '" + phone + "', 'ATIVO', 0, now(), now())";
    }

    private String inserirCustomerV2(String id, String phoneE164) {
        return "INSERT INTO customers (id, business_id, phone_e164, first_name, last_name, phone, "
                + "status, total_atendimentos, criado_em, atualizado_em) VALUES "
                + "('" + id + "', '" + PILOT + "', '" + phoneE164 + "', 'Ana', 'Paula', "
                + "'5511999990001', 'ATIVO', 0, now(), now())";
    }

    private String causaMensagem(Throwable t) {
        Throwable cause = t.getCause();
        StringBuilder sb = new StringBuilder();
        while (cause != null) {
            sb.append(" ").append(cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString();
    }
}
