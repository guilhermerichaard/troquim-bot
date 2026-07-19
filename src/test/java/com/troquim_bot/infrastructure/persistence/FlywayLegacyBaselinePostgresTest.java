package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a estratégia de PRIMEIRA ADOÇÃO do Flyway em banco legado, no PostgreSQL
 * REAL (Testcontainers) e pela fiação REAL do profile {@code azure}
 * (application-azure.properties + a variável TROQUIM_FLYWAY_BASELINE_ON_MIGRATE).
 *
 * O bug corrigido: com {@code baseline-version=0} o Flyway marcava o baseline em
 * v0 e reexecutava a V1 ({@code CREATE TABLE customers}) por cima do schema legado
 * — {@code relation "customers" already exists}. Com {@code baseline-version=1} o
 * baseline marca a V1 como aplicada e o Flyway aplica APENAS a V2.
 *
 * Cada teste sobe um contexto Spring completo com {@code ddl-auto=validate}: um
 * boot bem-sucedido é, por si só, a prova de que o Hibernate validou o schema.
 *
 * Teste A — banco vazio: baseline não dispara; V1 e V2 rodam; validate passa.
 * Teste B — banco legado (tabelas, SEM history): baseline v1; V1 pulada, V2 aplicada;
 *           dados preservados; history = baseline v1 + V2; validate passa.
 * Teste C — segurança operacional: legado com baseline-on-migrate=false FALHA no
 *           boot sem alterar o schema (a adoção precisa ser explicitamente ligada).
 *
 * Desabilita-se sem Docker (disabledWithoutDocker) para não quebrar CI sem Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayLegacyBaselinePostgresTest {

    @Container
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    private static final String PILOT = "11111111-1111-1111-1111-111111111111";

    /** Base 100% limpa antes de cada teste (dropa tabelas E o flyway_schema_history). */
    @BeforeEach
    void limparSchema() {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
    }

    // ==================== Teste A — banco vazio ====================

    @Test
    void testeA_bancoVazio_baselineNaoDispara_rodaV1eV2_validatePassa() throws SQLException {
        // Schema vazio; baseline-on-migrate ausente → false. O baseline NÃO dispara
        // (só dispara em schema NÃO vazio) → V1 e V2 rodam do zero.
        try (ConfigurableApplicationContext ctx = startAzureContext(null)) {
            // Boot com ddl-auto=validate concluído == Hibernate validate passou.
        }

        // customers passou a ter business_id e phone_e164 (V2 aplicada).
        assertTrue(colunaExiste("customers", "business_id"), "V2 deve ter adicionado business_id");
        assertTrue(colunaExiste("customers", "phone_e164"), "V2 deve ter adicionado phone_e164");

        // V1 e V2 foram executadas como migrations SQL (NÃO como baseline).
        assertEquals("SQL", tipoDaVersao("1"), "em banco vazio a V1 executa (SQL), não é baseline");
        assertEquals("SQL", tipoDaVersao("2"), "a V2 executa (SQL)");
        assertFalse(existeVersao("1", "BASELINE"), "não deve haver baseline em banco vazio");

        // Constraint de unicidade lógica aplicada.
        assertTrue(uniqueConstraintExiste(), "UNIQUE (business_id, phone_e164) deve existir");
    }

    // ==================== Teste B — banco legado ====================

    @Test
    void testeB_bancoLegado_baselineV1_pulaV1_aplicaV2_preservaDados() throws SQLException {
        criarSchemaLegadoSemHistory();

        // Dados legados: um Customer (schema V1), um Reservation e um Appointment.
        exec(inserirCustomerLegado("aaaaaaaa-0000-0000-0000-000000000001", "5511999990001"));
        exec(inserirReservationLegado("dddddddd-0000-0000-0000-000000000001",
                "aaaaaaaa-0000-0000-0000-000000000001"));
        exec(inserirAppointmentLegado("eeeeeeee-0000-0000-0000-000000000001",
                "aaaaaaaa-0000-0000-0000-000000000001"));

        // Primeira adoção: baseline-on-migrate EXPLICITAMENTE ligado.
        try (ConfigurableApplicationContext ctx = startAzureContext("true")) {
            // Boot com ddl-auto=validate concluído == Hibernate validate passou.
        }

        // Dados permanecem — nada foi descartado.
        assertEquals(1, contar("customers"), "o Customer legado deve permanecer");
        assertEquals(1, contar("reservations"), "a Reservation legada deve permanecer");
        assertEquals(1, contar("appointments"), "o Appointment legado deve permanecer");

        // History: baseline na v1 + V2 aplicada; a V1 NÃO foi reexecutada como SQL.
        assertTrue(existeVersao("1", "BASELINE"), "a v1 deve constar como BASELINE (schema legado)");
        assertEquals("SQL", tipoDaVersao("2"), "a V2 deve ter sido aplicada como SQL");
        assertFalse(existeVersao("1", "SQL"),
                "a V1 NÃO deve ser reexecutada como SQL sobre o schema legado");

        // business_id e phone_e164 preenchidos pelo backfill da V2.
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT business_id, phone_e164 FROM customers")) {
            assertTrue(rs.next(), "o Customer legado deve continuar existindo");
            assertEquals(PILOT, rs.getString("business_id"), "business_id deve receber o backfill do piloto");
            assertEquals("+5511999990001", rs.getString("phone_e164"), "phone_e164 deve ser canônico E.164");
        }

        // Constraints da V2 aplicadas de fato.
        assertTrue(uniqueConstraintExiste(), "UNIQUE (business_id, phone_e164) deve existir após V2");
        assertTrue(notNullAplicado("business_id"), "business_id deve ser NOT NULL após V2");
    }

    // ==================== Teste C — segurança operacional ====================

    @Test
    void testeC_bancoLegado_baselineFalse_falhaSemAlterarSchema() throws SQLException {
        criarSchemaLegadoSemHistory();

        // baseline-on-migrate=false (padrão) sobre schema legado sem history → o Flyway
        // aborta e o contexto NÃO sobe. Prova de que a adoção precisa ser explícita.
        assertThrows(RuntimeException.class, () -> {
            try (ConfigurableApplicationContext ctx = startAzureContext("false")) {
                // não deve chegar aqui
            }
        });

        // Schema INALTERADO: V2 não rodou (sem business_id) e nem o history foi criado.
        assertFalse(colunaExiste("customers", "business_id"),
                "o schema legado não deve ser alterado quando o baseline não é ligado");
        assertFalse(tabelaExiste("flyway_schema_history"),
                "o flyway_schema_history não deve ser criado quando o Flyway aborta");
    }

    // ==================== infra do contexto Spring ====================

    /**
     * Sobe um contexto Spring (sem web) no profile azure contra o container.
     * {@code baselineOnMigrate} nulo → variável ausente (default false do profile).
     */
    private ConfigurableApplicationContext startAzureContext(String baselineOnMigrate) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(TroquimBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("azure");

        java.util.List<String> props = new java.util.ArrayList<>(java.util.List.of(
                "spring.datasource.url=" + PG.getJdbcUrl(),
                "spring.datasource.username=" + PG.getUsername(),
                "spring.datasource.password=" + PG.getPassword(),
                // azure exige o tenant do piloto (resolve o tenant e o placeholder da V2)...
                "TROQUIM_PILOT_BUSINESS_ID=" + PILOT,
                // ...e a chave administrativa (sem default no azure).
                "TROQUIM_ADMIN_API_KEY=test-admin-key-for-azure"));
        if (baselineOnMigrate != null) {
            props.add("TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=" + baselineOnMigrate);
        }
        return builder.properties(props.toArray(String[]::new)).run();
    }

    // ==================== construção do estado legado ====================

    /**
     * Reproduz o "dump legado": aplica a V1 (schema exatamente igual ao gerado pelo
     * ddl-auto anterior) e então REMOVE o flyway_schema_history — deixando as tabelas
     * legadas SEM histórico, exatamente como a base da Droplet criada por ddl-auto.
     */
    private void criarSchemaLegadoSemHistory() throws SQLException {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();
        exec("DROP TABLE flyway_schema_history");
    }

    // ==================== helpers de JDBC / asserções ====================

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private int contar(String tabela) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean colunaExiste(String tabela, String coluna) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT 1 FROM information_schema.columns WHERE table_name = '"
                             + tabela + "' AND column_name = '" + coluna + "'")) {
            return rs.next();
        }
    }

    private boolean tabelaExiste(String tabela) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT 1 FROM information_schema.tables WHERE table_name = '" + tabela + "'")) {
            return rs.next();
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

    private boolean notNullAplicado(String coluna) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT is_nullable FROM information_schema.columns "
                             + "WHERE table_name = 'customers' AND column_name = '" + coluna + "'")) {
            return rs.next() && "NO".equals(rs.getString("is_nullable"));
        }
    }

    /** O {@code type} do flyway_schema_history para uma dada versão (BASELINE/SQL), ou null. */
    private String tipoDaVersao(String version) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT type FROM flyway_schema_history WHERE version = '" + version + "'")) {
            return rs.next() ? rs.getString("type") : null;
        }
    }

    private boolean existeVersao(String version, String type) throws SQLException {
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT 1 FROM flyway_schema_history WHERE version = '" + version
                             + "' AND type = '" + type + "'")) {
            return rs.next();
        }
    }

    // ==================== inserts legados (schema V1) ====================

    private String inserirCustomerLegado(String id, String phone) {
        // Schema V1: customers ainda sem business_id/phone_e164.
        return "INSERT INTO customers (id, first_name, last_name, phone, status, total_atendimentos, "
                + "criado_em, atualizado_em) VALUES "
                + "('" + id + "', 'Ana', 'Paula', '" + phone + "', 'ATIVO', 0, now(), now())";
    }

    private String inserirReservationLegado(String id, String customerId) {
        return "INSERT INTO reservations (id, customer_id, professional_id, service_id, availability_id, "
                + "date, start_time, end_time, expires_at, status, criado_em, atualizado_em) VALUES "
                + "('" + id + "', '" + customerId + "', "
                + "'11111111-2222-3333-4444-555555555555', "
                + "'22222222-3333-4444-5555-666666666666', "
                + "'33333333-4444-5555-6666-777777777777', "
                + "DATE '2026-01-10', TIME '10:00', TIME '11:00', now(), 'ATIVA', now(), now())";
    }

    private String inserirAppointmentLegado(String id, String customerId) {
        return "INSERT INTO appointments (id, customer_id, professional_id, service_id, availability_id, "
                + "date, start_time, end_time, status, criado_em, atualizado_em) VALUES "
                + "('" + id + "', '" + customerId + "', "
                + "'11111111-2222-3333-4444-555555555555', "
                + "'22222222-3333-4444-5555-666666666666', "
                + "'33333333-4444-5555-6666-777777777777', "
                + "DATE '2026-01-10', TIME '10:00', TIME '11:00', 'AGENDADO', now(), now())";
    }
}
