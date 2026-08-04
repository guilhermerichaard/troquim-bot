package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessStatus;
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business (raiz de identidade do negócio) em PostgreSQL real (Testcontainers), sem H2.
 *
 * Cobre o que só o banco de verdade prova: o adapter de produção é o JPA, o negócio
 * SOBREVIVE ao reinício do contexto com todos os campos reconstituídos, dois negócios
 * permanecem isolados, e o piloto existe mesmo antes de qualquer dado tenant-scoped ser
 * escrito.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Business - persistência em PostgreSQL real")
class BusinessRootPostgresPersistenceTest {

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
    @DisplayName("o piloto existe mesmo antes de qualquer dado tenant-scoped ser escrito")
    void pilotoExisteEmBancoVazio() {
        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);

            assertThat(negocios).isInstanceOf(JpaBusinessRepository.class);
            assertThat(negocios.exists(PILOTO)).isTrue();

            Business piloto = negocios.findById(PILOTO);
            assertThat(piloto).isNotNull();
            assertThat(piloto.getStatus()).isEqualTo(BusinessStatus.ATIVO);
        }
    }

    @Test
    @DisplayName("Business sobrevive ao reinício: nome, contato, status e timestamps reconstituídos")
    void businessSobreviveAoReinicio() {
        BusinessId id = BusinessId.from(UUID.randomUUID());

        // ---- Contexto A: cadastra e ativa ----
        try (ConfigurableApplicationContext ctxA = startContext()) {
            BusinessRepository negocios = ctxA.getBean(BusinessRepository.class);

            Business novo = new Business(id, "Salão da Ana", "+5511999990000", "Rua A, 100");
            negocios.save(novo);

            Business paraAtivar = negocios.findById(id);
            paraAtivar.ativar();
            negocios.save(paraAtivar);
        }

        // ---- Contexto B: reinício, MESMO banco ----
        try (ConfigurableApplicationContext ctxB = startContext()) {
            BusinessRepository negocios = ctxB.getBean(BusinessRepository.class);

            Business recarregado = negocios.findById(id);
            assertThat(recarregado).isNotNull();
            assertThat(recarregado.getId()).isEqualTo(id);
            assertThat(recarregado.getNome()).isEqualTo("Salão da Ana");
            assertThat(recarregado.getTelefone()).isEqualTo("+5511999990000");
            assertThat(recarregado.getEndereco()).isEqualTo("Rua A, 100");
            assertThat(recarregado.getStatus()).isEqualTo(BusinessStatus.ATIVO);
            assertThat(recarregado.getCriadoEm()).isNotNull();
            assertThat(recarregado.getAtualizadoEm()).isNotNull();
        }
    }

    @Test
    @DisplayName("contato pode ficar incompleto: telefone e endereço nulos são aceitos e reconstituídos")
    void contatoIncompletoEhAceito() {
        BusinessId id = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            negocios.save(new Business(id, "Negócio Recém-Cadastrado", null, null));

            Business recarregado = negocios.findById(id);
            assertThat(recarregado.getTelefone()).isNull();
            assertThat(recarregado.getEndereco()).isNull();
        }
    }

    @Test
    @DisplayName("dois negócios permanecem isolados: dados de um não vazam para o outro")
    void doisNegociosIsolados() {
        BusinessId a = BusinessId.from(UUID.randomUUID());
        BusinessId b = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);

            negocios.save(new Business(a, "Negócio A", "+5511900000001", null));
            negocios.save(new Business(b, "Negócio B", "+5511900000002", null));

            Business recarregadoA = negocios.findById(a);
            Business recarregadoB = negocios.findById(b);

            assertThat(recarregadoA.getNome()).isEqualTo("Negócio A");
            assertThat(recarregadoB.getNome()).isEqualTo("Negócio B");
            assertThat(recarregadoA.getTelefone()).isNotEqualTo(recarregadoB.getTelefone());
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa um serviço gravado sob um BusinessId órfão, mesmo por carga direta")
    void bancoRecusaBusinessIdOrfaoEmServico() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            javax.sql.DataSource dataSource = ctx.getBean(javax.sql.DataSource.class);
            UUID orfao = UUID.randomUUID();

            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                String sql = String.format("""
                        insert into services (id, business_id, nome, duracao_minutos, status, criado_em, atualizado_em)
                        values ('%s', '%s', 'Servico Orfao', 30, 'ATIVO', now(), now())""",
                        UUID.randomUUID(), orfao);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> stmt.executeUpdate(sql))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("fk_services_business");
            }
        }
    }

    @Test
    @DisplayName("toda tabela com business_id tem FK para businesses")
    void todasAsTabelasComBusinessIdTemFk() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            javax.sql.DataSource dataSource = ctx.getBean(javax.sql.DataSource.class);

            String[] fks = {
                    "fk_customers_business", "fk_booking_idempotency_business",
                    "fk_whatsapp_flow_sessions_business", "fk_appointments_business",
                    "fk_reservations_business", "fk_whatsapp_channel_connections_business",
                    "fk_owner_users_business", "fk_owner_sessions_business",
                    "fk_services_business", "fk_professionals_business",
                    "fk_professional_services_business", "fk_business_hours_business",
                    "fk_professional_availability_business"
            };

            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {
                for (String fk : fks) {
                    try (ResultSet rs = stmt.executeQuery(
                            "select count(*) from pg_constraint where conname = '" + fk + "'")) {
                        rs.next();
                        assertThat(rs.getInt(1)).as("FK ausente: " + fk).isEqualTo(1);
                    }
                }
            }
        }
    }
}
