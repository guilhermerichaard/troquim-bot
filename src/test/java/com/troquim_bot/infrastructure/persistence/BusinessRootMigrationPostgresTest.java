package com.troquim_bot.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GATE DA MIGRATION V13 — prova que ela sobe tanto em banco LIMPO quanto sobre o histórico
 * V1–V12 já populado, e que materializa em {@code businesses} todo BusinessId já em uso.
 *
 * Diferente dos demais testes de Business (que sobem V1→V13 num banco limpo pelo Spring),
 * este dirige o Flyway diretamente em DUAS etapas contra um PostgreSQL real: migra só até
 * V12, insere dado tenant-scoped REALISTA via JDBC (como um negócio já em operação antes
 * desta migration existir), migra V13, e verifica que a linha técnica apareceu — sem
 * inventar nome de cliente.
 */
@DisplayName("Migration V13 - raiz de negócio (PostgreSQL real)")
class BusinessRootMigrationPostgresTest {

    private static final UUID PILOTO = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

    /**
     * Cada teste dirige sua PRÓPRIA sequência de {@code target(...)}: sem resetar o schema,
     * o segundo teste encontraria o banco já migrado pelo primeiro, e {@code migrate()} não
     * volta versão. Recriar o schema público é mais barato que subir um container por teste.
     */
    @BeforeEach
    void resetSchema() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("pilot_business_id", PILOTO.toString()))
                .target(target)
                .load();
    }

    @Test
    @DisplayName("V13 sobe em banco limpo e cria o piloto mesmo sem nenhum dado tenant-scoped")
    void v13SobeEmBancoLimpo() throws SQLException {
        flyway("13").migrate();

        assertTrue(tabelaExiste("businesses"), "tabela businesses precisa existir");
        assertEquals(1, contar("SELECT count(*) FROM businesses WHERE id = '" + PILOTO + "'"),
                "o piloto precisa existir mesmo em banco vazio");
        assertEquals("ATIVO", statusDoNegocio(PILOTO));
    }

    @Test
    @DisplayName("V13 sobe sobre o histórico completo V1–V12, sem quebrar o que já existia")
    void v13SobeSobreOHistorico() throws SQLException {
        // 1. Migra até V12 — o schema exatamente como era antes desta feature existir.
        flyway("12").migrate();
        assertTrue(tabelaExiste("services"), "pré-condição: V11/V12 já aplicadas");
        assertTrue(!tabelaExiste("businesses"), "pré-condição: businesses ainda não existe");

        // 2. Migra V13 por cima do histórico populado por V1–V12.
        flyway("13").migrate();

        assertTrue(tabelaExiste("businesses"));
        assertEquals(1, contar("SELECT count(*) FROM flyway_schema_history "
                + "WHERE version = '13' AND success = true"));
    }

    @Test
    @DisplayName("dado tenant-scoped já existente antes da V13 é materializado em businesses")
    void dadosTenantExistentesSaoMaterializados() throws SQLException {
        // 1. Migra só até V12 — nenhuma tabela businesses ainda existe.
        flyway("12").migrate();

        UUID negocioJaEmOperacao = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();

        // 2. Insere dado tenant-scoped REALISTA, como um negócio que já operava antes da
        // V13 ser escrita — cenário do backfill em banco não vazio.
        try (Connection c = conn()) {
            try (var ps = c.prepareStatement("""
                    INSERT INTO services (id, business_id, nome, duracao_minutos, status,
                        criado_em, atualizado_em)
                    VALUES (?, ?, 'Corte', 30, 'ATIVO', now(), now())""")) {
                ps.setObject(1, servicoId);
                ps.setObject(2, negocioJaEmOperacao);
                ps.executeUpdate();
            }
        }

        // 3. Migra V13: o BusinessId já em uso precisa virar linha técnica em businesses.
        flyway("13").migrate();

        assertEquals(1, contar("SELECT count(*) FROM businesses WHERE id = '"
                + negocioJaEmOperacao + "'"), "o negócio já em operação precisa ter sido materializado");
        assertEquals("ATIVO", statusDoNegocio(negocioJaEmOperacao),
                "dado já existente representa operação em curso — status ATIVO, não TRIAL");

        String nome = nomeDoNegocio(negocioJaEmOperacao);
        assertTrue(nome.startsWith("Negocio migrado"), "nome técnico neutro, sem dado de cliente: " + nome);
        assertTrue(!nome.toLowerCase().contains("gizelle") && !nome.toLowerCase().contains("dayana")
                && !nome.toLowerCase().contains("malu") && !nome.toLowerCase().contains("unhas divas"),
                "nenhum dado de cliente pode vazar para o nome técnico");
    }

    @Test
    @DisplayName("depois da V13, o PostgreSQL recusa BusinessId órfão por carga direta")
    void bancoRecusaBusinessIdOrfaoAposV13() throws SQLException {
        flyway("13").migrate();

        UUID orfao = UUID.randomUUID();
        try (Connection c = conn()) {
            assertThrows(SQLException.class, () -> {
                try (var ps = c.prepareStatement("""
                        INSERT INTO professionals (id, business_id, nome, status, criado_em, atualizado_em)
                        VALUES (?, ?, 'Profissional Orfao', 'ATIVO', now(), now())""")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, orfao);
                    ps.executeUpdate();
                }
            }, "business_id sem linha em businesses precisa ser recusado pela FK");
        }
    }

    // ==================== leitura ====================

    private String statusDoNegocio(UUID id) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT status FROM businesses WHERE id = '" + id + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private String nomeDoNegocio(UUID id) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nome FROM businesses WHERE id = '" + id + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private long contar(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean tabelaExiste(String nome) throws SQLException {
        return contar("SELECT count(*) FROM information_schema.tables WHERE table_name = '" + nome + "'") == 1;
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
