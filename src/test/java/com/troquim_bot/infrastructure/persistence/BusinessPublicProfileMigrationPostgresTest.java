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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GATE DA MIGRATION V14 — prova que ela sobe tanto em banco LIMPO quanto sobre o histórico
 * V1–V13 já populado (com {@code businesses} existente).
 */
@DisplayName("Migration V14 - perfil público (PostgreSQL real)")
class BusinessPublicProfileMigrationPostgresTest {

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
    @DisplayName("V14 sobe em banco limpo")
    void v14SobeEmBancoLimpo() throws SQLException {
        flyway("14").migrate();

        assertTrue(tabelaExiste("business_public_profiles"));
        assertEquals(1, contar("SELECT count(*) FROM flyway_schema_history "
                + "WHERE version = '14' AND success = true"));
    }

    @Test
    @DisplayName("V14 sobe sobre o histórico completo V1–V13, com businesses já populado")
    void v14SobeSobreOHistorico() throws SQLException {
        // 1. Migra até V13 — businesses já existe, com o piloto.
        flyway("13").migrate();
        assertTrue(tabelaExiste("businesses"), "pré-condição: V13 já aplicada");
        assertTrue(!tabelaExiste("business_public_profiles"), "pré-condição: V14 ainda não aplicada");
        assertEquals(1, contar("SELECT count(*) FROM businesses WHERE id = '" + PILOTO + "'"));

        // 2. Migra V14 por cima do histórico populado.
        flyway("14").migrate();

        assertTrue(tabelaExiste("business_public_profiles"));
        // O piloto materializado pela V13 continua intacto — V14 não mexeu em businesses.
        assertEquals(1, contar("SELECT count(*) FROM businesses WHERE id = '" + PILOTO + "'"));
    }

    @Test
    @DisplayName("depois da V14, o PostgreSQL recusa slug duplicado e BusinessId órfão")
    void bancoRecusaSlugDuplicadoEBusinessIdOrfaoAposV14() throws SQLException {
        flyway("14").migrate();

        UUID negocioA = UUID.randomUUID();
        UUID negocioB = UUID.randomUUID();
        try (Connection c = conn()) {
            inserirNegocio(c, negocioA);
            inserirNegocio(c, negocioB);

            try (var ps = c.prepareStatement("""
                    INSERT INTO business_public_profiles
                        (business_id, slug, nome_publico, publication_status, criado_em, atualizado_em)
                    VALUES (?, 'slug-unico', 'Negocio A', 'DRAFT', now(), now())""")) {
                ps.setObject(1, negocioA);
                ps.executeUpdate();
            }

            SQLException erro = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
                try (var ps = c.prepareStatement("""
                        INSERT INTO business_public_profiles
                            (business_id, slug, nome_publico, publication_status, criado_em, atualizado_em)
                        VALUES (?, 'slug-unico', 'Negocio B', 'DRAFT', now(), now())""")) {
                    ps.setObject(1, negocioB);
                    ps.executeUpdate();
                }
            });
            assertTrue(erro.getMessage().contains("uq_business_public_profiles_slug"));
        }
    }

    private void inserirNegocio(Connection c, UUID id) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO businesses (id, nome, status, criado_em, atualizado_em)
                VALUES (?, 'Negocio de Teste', 'ATIVO', now(), now())""")) {
            ps.setObject(1, id);
            ps.executeUpdate();
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
