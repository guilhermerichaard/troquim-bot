package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catálogo persistido em PostgreSQL real (Testcontainers), sem H2.
 *
 * Cobre o que só o banco de verdade prova: reconstrução fiel dos agregados, preço ausente
 * vs presente, o vínculo profissional x serviço, e — o mais importante — que o PRÓPRIO
 * PostgreSQL recusa associação cruzada entre negócios, mesmo que a camada Java erre.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Catálogo - persistência em PostgreSQL real")
class CatalogoPostgresPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    private static final BusinessId NEGOCIO_A = TestTenants.PILOT;
    private static final BusinessId NEGOCIO_B = TestTenants.OUTRO;

    private ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(TroquimBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("azure")
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "TROQUIM_PILOT_BUSINESS_ID=" + NEGOCIO_A.getValue(),
                        "TROQUIM_ADMIN_API_KEY=test-admin-key-for-azure")
                .run();
    }

    @Test
    @DisplayName("agregados sobrevivem ao reinício com preço, habilitações e status intactos")
    void catalogoEHabilitacoesSobrevivemAoReinicio() {
        ServiceId idSemPreco;
        ServiceId idComPreco;
        ProfessionalId idProfissional;

        // ---- Contexto A: grava ----
        try (ConfigurableApplicationContext ctxA = startContext()) {
            ServiceRepository servicos = ctxA.getBean(ServiceRepository.class);
            ProfessionalRepository profissionais = ctxA.getBean(ProfessionalRepository.class);

            // O adapter de produção precisa ser o JPA, não o duplo em memória.
            assertThat(servicos).isInstanceOf(JpaServiceRepository.class);
            assertThat(profissionais).isInstanceOf(JpaProfessionalRepository.class);

            Service semPreco = servicos.salvar(Service.novoSemPreco(
                    ServiceId.generate(), NEGOCIO_A, "Unhas", "Alongamento e esmaltação",
                    ServiceDuration.ofMinutes(60)));
            Service comPreco = servicos.salvar(Service.novoComPreco(
                    ServiceId.generate(), NEGOCIO_A, "Cabelo", null,
                    ServiceDuration.ofMinutes(90), Money.of(120.50, "BRL")));

            Professional malu = new Professional(ProfessionalId.generate(), NEGOCIO_A,
                    "Profissional Teste", Set.of(semPreco.getId()), Set.of("descritivo"),
                    "+5511999990000");
            profissionais.salvar(malu);

            idSemPreco = semPreco.getId();
            idComPreco = comPreco.getId();
            idProfissional = malu.getId();
        }

        // ---- Contexto B: reinício, MESMO banco ----
        try (ConfigurableApplicationContext ctxB = startContext()) {
            ServiceRepository servicos = ctxB.getBean(ServiceRepository.class);
            ProfessionalRepository profissionais = ctxB.getBean(ProfessionalRepository.class);

            Service semPreco = servicos.buscarPorId(NEGOCIO_A, idSemPreco).orElseThrow();
            assertThat(semPreco.getNome()).isEqualTo("Unhas");
            assertThat(semPreco.getDescricao()).isEqualTo("Alongamento e esmaltação");
            assertThat(semPreco.getDuracao().getMinutes()).isEqualTo(60);
            assertThat(semPreco.isAtivo()).isTrue();
            // Preço ausente reaparece como ausência — nunca como zero.
            assertThat(semPreco.getPreco()).isEmpty();
            assertThat(semPreco.temPreco()).isFalse();

            Service comPreco = servicos.buscarPorId(NEGOCIO_A, idComPreco).orElseThrow();
            assertThat(comPreco.getPreco()).isPresent();
            assertThat(comPreco.getPreco().orElseThrow().getAmount())
                    .isEqualByComparingTo("120.50");
            assertThat(comPreco.getPreco().orElseThrow().getCurrency()).isEqualTo("BRL");
            assertThat(comPreco.getDuracao().getMinutes()).isEqualTo(90);

            // Habilitação sobrevive ao restart.
            Professional malu = profissionais.buscarPorId(NEGOCIO_A, idProfissional).orElseThrow();
            assertThat(malu.getServicosHabilitados()).containsExactly(idSemPreco);
            assertThat(malu.atende(idSemPreco)).isTrue();
            assertThat(malu.atende(idComPreco)).isFalse();
            assertThat(malu.getEspecialidades()).containsExactly("descritivo");

            assertThat(profissionais.listarAtivosPorServico(NEGOCIO_A, idSemPreco))
                    .extracting(Professional::getId).containsExactly(idProfissional);
            assertThat(profissionais.listarAtivosPorServico(NEGOCIO_A, idComPreco)).isEmpty();
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa associar profissional do negócio A a serviço do negócio B")
    void bancoRecusaAssociacaoCruzadaEntreNegocios() throws SQLException {
        UUID profissionalA;
        UUID servicoB;

        try (ConfigurableApplicationContext ctx = startContext()) {
            ServiceRepository servicos = ctx.getBean(ServiceRepository.class);
            ProfessionalRepository profissionais = ctx.getBean(ProfessionalRepository.class);

            Professional doA = new Professional(ProfessionalId.generate(), NEGOCIO_A,
                    "Profissional do A", Set.of(), Set.of(), "+5511999990001");
            profissionais.salvar(doA);

            Service doB = servicos.salvar(Service.novoSemPreco(
                    ServiceId.generate(), NEGOCIO_B, "Serviço do B", null,
                    ServiceDuration.ofMinutes(60)));

            profissionalA = doA.getId().getValue();
            servicoB = doB.getId().getValue();

            // Tentativa de gravar a associação cruzada DIRETO no banco, contornando toda a
            // camada Java: é isso que prova que o isolamento não depende do código.
            DataSource dataSource = ctx.getBean(DataSource.class);
            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                // (business_id = A, service_id = do B) não satisfaz a FK composta
                // (business_id, service_id) -> services.
                String sqlCruzado = String.format(
                        "insert into professional_services (business_id, professional_id, service_id) "
                                + "values ('%s','%s','%s')",
                        NEGOCIO_A.getValue(), profissionalA, servicoB);

                assertThatThrownBy(() -> stmt.executeUpdate(sqlCruzado))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("fk_professional_services_service");
            }

            // E a associação legítima, no mesmo negócio, continua funcionando.
            Service doA_servico = servicos.salvar(Service.novoSemPreco(
                    ServiceId.generate(), NEGOCIO_A, "Serviço do A", null,
                    ServiceDuration.ofMinutes(60)));
            doA.habilitarPara(doA_servico.getId());
            profissionais.salvar(doA);

            assertThat(profissionais.buscarPorId(NEGOCIO_A, doA.getId()).orElseThrow()
                    .getServicosHabilitados()).containsExactly(doA_servico.getId());
        }
    }

    @Test
    @DisplayName("provisionamento é idempotente: rodar duas vezes não duplica nem sobrescreve")
    void provisionamentoEhIdempotente() {
        List<ProvisionarNegocio.ServicoDesejado> servicos = List.of(
                new ProvisionarNegocio.ServicoDesejado("Unhas", 60),
                new ProvisionarNegocio.ServicoDesejado("Sobrancelha", 30));
        ProvisionarNegocio.ProfissionalDesejado profissional =
                new ProvisionarNegocio.ProfissionalDesejado(
                        "Profissional Piloto", "+5511999990002", List.of("Unhas", "Sobrancelha"));

        BusinessId alvo = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            ProvisionarNegocio provisionar = ctx.getBean(ProvisionarNegocio.class);
            ServiceRepository repoServicos = ctx.getBean(ServiceRepository.class);
            ProfessionalRepository repoProfissionais = ctx.getBean(ProfessionalRepository.class);

            ProvisionarNegocio.Resultado primeira = provisionar.provisionar(alvo, servicos, profissional);
            assertThat(primeira.servicosCriados()).hasSize(2);
            assertThat(primeira.profissionalCriado()).isPresent();
            assertThat(primeira.habilitacoesAdicionadas()).containsExactlyInAnyOrder("Unhas", "Sobrancelha");
            assertThat(primeira.fezAlgo()).isTrue();

            // Segunda execução: nada novo.
            ProvisionarNegocio.Resultado segunda = provisionar.provisionar(alvo, servicos, profissional);
            assertThat(segunda.servicosCriados()).isEmpty();
            assertThat(segunda.servicosJaExistentes()).hasSize(2);
            assertThat(segunda.profissionalCriado()).isEmpty();
            assertThat(segunda.profissionalJaExistente()).isPresent();
            assertThat(segunda.habilitacoesAdicionadas()).isEmpty();
            assertThat(segunda.fezAlgo()).isFalse();

            // Estado final: exatamente um de cada, com as duas habilitações.
            assertThat(repoServicos.listarAtivos(alvo)).hasSize(2);
            assertThat(repoProfissionais.listarAtivos(alvo)).hasSize(1);
            assertThat(repoProfissionais.listarAtivos(alvo).get(0).getServicosHabilitados()).hasSize(2);

            // O provisionamento ficou contido no negócio alvo: um negócio novo, que nunca
            // foi provisionado, continua sem catálogo nenhum.
            // (Usa um id fresco de propósito — NEGOCIO_B recebe dados de outro teste, e
            // os testes compartilham o mesmo container PostgreSQL.)
            BusinessId nuncaProvisionado = BusinessId.from(UUID.randomUUID());
            assertThat(repoServicos.listarAtivos(nuncaProvisionado)).isEmpty();
            assertThat(repoProfissionais.listarAtivos(nuncaProvisionado)).isEmpty();

            // E tudo que foi criado pertence de fato ao alvo.
            assertThat(repoServicos.listarAtivos(alvo))
                    .allSatisfy(s -> assertThat(s.pertenceAo(alvo)).isTrue());
        }
    }
}
