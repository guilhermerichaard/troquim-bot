package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import com.troquim_bot.application.business.ConfigurarPerfilPublico;
import com.troquim_bot.application.business.PublicarPerfilPublico;
import com.troquim_bot.application.business.SlugIndisponivelException;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.PublicationStatus;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Perfil público em PostgreSQL real (Testcontainers), sem H2.
 *
 * Cobre o que só o banco de verdade prova: FK para businesses, unicidade GLOBAL do slug (com
 * conflito controlado quando a Application recebe a violação), sobrevivência ao reinício do
 * contexto, isolamento entre dois negócios e que mudar o slug libera o antigo de verdade.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Perfil público - persistência em PostgreSQL real")
class BusinessPublicProfilePostgresPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    private static final BusinessId PILOTO = TestTenants.PILOT;

    private ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(TroquimBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("azure")
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "TROQUIM_PILOT_BUSINESS_ID=" + PILOTO.getValue(),
                        "TROQUIM_ADMIN_API_KEY=test-admin-key-for-azure")
                .run();
    }

    @Test
    @DisplayName("perfil pertence obrigatoriamente a um Business existente")
    void perfilPertenceObrigatoriamenteABusinessExistente() {
        try (ConfigurableApplicationContext ctx = startContext()) {
            ConfigurarPerfilPublico configurar = ctx.getBean(ConfigurarPerfilPublico.class);
            BusinessId inexistente = BusinessId.from(UUID.randomUUID());

            assertThatThrownBy(() -> configurar.configurar(
                    inexistente, "salao-x", "Salão X", null, null, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("perfil SOBREVIVE ao reinício, com todos os campos reconstituídos")
    void perfilSobreviveAoReinicio() {
        BusinessId negocio = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctxA = startContext()) {
            BusinessRepository negocios = ctxA.getBean(BusinessRepository.class);
            negocios.save(new Business(negocio, "Salão da Ana", null, null));

            ConfigurarPerfilPublico configurar = ctxA.getBean(ConfigurarPerfilPublico.class);
            PublicarPerfilPublico publicar = ctxA.getBean(PublicarPerfilPublico.class);
            BusinessPublicProfileRepository perfis = ctxA.getBean(BusinessPublicProfileRepository.class);
            assertThat(perfis).isInstanceOf(JpaBusinessPublicProfileRepository.class);

            configurar.configurar(negocio, "Salão da Ana", "Salão da Ana",
                    "Descrição curta", "+5511999990000", "Rua A, 100");
            publicar.publicar(negocio);
        }

        try (ConfigurableApplicationContext ctxB = startContext()) {
            BusinessPublicProfileRepository perfis = ctxB.getBean(BusinessPublicProfileRepository.class);

            BusinessPublicProfile recarregado = perfis.buscarPorBusinessId(negocio).orElseThrow();
            assertThat(recarregado.getSlug().getValue()).isEqualTo("salao-da-ana");
            assertThat(recarregado.getNomePublico()).isEqualTo("Salão da Ana");
            assertThat(recarregado.getDescricaoCurta()).isEqualTo("Descrição curta");
            assertThat(recarregado.getTelefonePublico()).isEqualTo("+5511999990000");
            assertThat(recarregado.getEnderecoPublico()).isEqualTo("Rua A, 100");
            assertThat(recarregado.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
            assertThat(recarregado.getCriadoEm()).isNotNull();
            assertThat(recarregado.getAtualizadoEm()).isNotNull();
        }
    }

    @Test
    @DisplayName("dois negócios permanecem isolados: perfil de um não vaza para o outro")
    void doisNegociosIsolados() {
        BusinessId a = BusinessId.from(UUID.randomUUID());
        BusinessId b = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            negocios.save(new Business(a, "Negócio A", null, null));
            negocios.save(new Business(b, "Negócio B", null, null));

            ConfigurarPerfilPublico configurar = ctx.getBean(ConfigurarPerfilPublico.class);
            configurar.configurar(a, "negocio-a-" + a.getValue().toString().substring(0, 6),
                    "Negócio A", null, null, null);
            configurar.configurar(b, "negocio-b-" + b.getValue().toString().substring(0, 6),
                    "Negócio B", null, null, null);

            BusinessPublicProfileRepository perfis = ctx.getBean(BusinessPublicProfileRepository.class);
            BusinessPublicProfile perfilA = perfis.buscarPorBusinessId(a).orElseThrow();
            BusinessPublicProfile perfilB = perfis.buscarPorBusinessId(b).orElseThrow();

            assertThat(perfilA.getNomePublico()).isEqualTo("Negócio A");
            assertThat(perfilB.getNomePublico()).isEqualTo("Negócio B");
            assertThat(perfilA.getSlug()).isNotEqualTo(perfilB.getSlug());
        }
    }

    @Test
    @DisplayName("mudar o slug de A libera o antigo: B consegue usá-lo depois")
    void mudarSlugLiberaOAntigoCorretamente() {
        BusinessId a = BusinessId.from(UUID.randomUUID());
        BusinessId b = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            negocios.save(new Business(a, "Negócio A", null, null));
            negocios.save(new Business(b, "Negócio B", null, null));

            String slugCompartilhavel = "compartilhavel-" + a.getValue().toString().substring(0, 6);
            ConfigurarPerfilPublico configurar = ctx.getBean(ConfigurarPerfilPublico.class);
            configurar.configurar(a, slugCompartilhavel, "Negócio A", null, null, null);

            // B tentando o mesmo slug AINDA em uso por A é recusado.
            assertThatThrownBy(() -> configurar.configurar(b, slugCompartilhavel, "Negócio B", null, null, null))
                    .isInstanceOf(SlugIndisponivelException.class);

            // A muda de slug — o antigo fica livre.
            configurar.configurar(a, "novo-slug-de-a-" + a.getValue().toString().substring(0, 6),
                    "Negócio A", null, null, null);

            // Agora B consegue usar o que A abandonou.
            BusinessPublicProfile perfilB = configurar.configurar(
                    b, slugCompartilhavel, "Negócio B", null, null, null);
            assertThat(perfilB.getSlug().getValue()).isEqualTo(slugCompartilhavel);
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa slug duplicado, mesmo por carga direta via JDBC")
    void bancoRecusaSlugDuplicadoViaJdbc() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            BusinessId a = BusinessId.from(UUID.randomUUID());
            BusinessId b = BusinessId.from(UUID.randomUUID());
            negocios.save(new Business(a, "Negócio A", null, null));
            negocios.save(new Business(b, "Negócio B", null, null));

            DataSource dataSource = ctx.getBean(DataSource.class);
            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                String slugDuplicado = "duplicado-" + a.getValue().toString().substring(0, 6);
                stmt.executeUpdate(String.format("""
                        insert into business_public_profiles
                            (business_id, slug, nome_publico, publication_status, criado_em, atualizado_em)
                        values ('%s', '%s', 'Negocio A', 'DRAFT', now(), now())""",
                        a.getValue(), slugDuplicado));

                String sqlDuplicado = String.format("""
                        insert into business_public_profiles
                            (business_id, slug, nome_publico, publication_status, criado_em, atualizado_em)
                        values ('%s', '%s', 'Negocio B', 'DRAFT', now(), now())""",
                        b.getValue(), slugDuplicado);

                assertThatThrownBy(() -> stmt.executeUpdate(sqlDuplicado))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_business_public_profiles_slug");
            }
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa BusinessId órfão no perfil público")
    void bancoRecusaBusinessIdOrfao() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            DataSource dataSource = ctx.getBean(DataSource.class);
            UUID orfao = UUID.randomUUID();

            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                String sql = String.format("""
                        insert into business_public_profiles
                            (business_id, slug, nome_publico, publication_status, criado_em, atualizado_em)
                        values ('%s', 'slug-orfao', 'Negocio Orfao', 'DRAFT', now(), now())""", orfao);

                assertThatThrownBy(() -> stmt.executeUpdate(sql))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("fk_business_public_profiles_business");
            }
        }
    }
}
