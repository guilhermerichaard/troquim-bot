package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.ProfessionalRepository;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expediente e disponibilidade em PostgreSQL real (Testcontainers), sem H2.
 *
 * Cobre o que só o banco de verdade prova:
 * <ul>
 *   <li>a V12 sobe sobre o histórico completo de migrations, não num schema inventado;</li>
 *   <li>o expediente e a disponibilidade SOBREVIVEM ao reinício do contexto;</li>
 *   <li>os Value Objects são reconstituídos por inteiro;</li>
 *   <li>o PRÓPRIO PostgreSQL recusa disponibilidade cruzada entre negócios, ainda que a
 *       camada Java erre.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Expediente e disponibilidade - persistência em PostgreSQL real")
class ExpedientePostgresPersistenceTest {

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

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    /** Semana com almoço na segunda, sábado curto e domingo fechado. */
    private static BusinessHours expedienteComAlmoco() {
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
        semana.put(DiaSemana.SEGUNDA, List.of(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0)));
        semana.put(DiaSemana.SABADO, List.of(periodo(9, 0, 14, 0)));
        return BusinessHours.deSemana(semana);
    }

    @Test
    @DisplayName("a V12 sobe sobre o histórico e cria as duas tabelas estruturais")
    void migrationV12SobeSobreOHistorico() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            DataSource dataSource = ctx.getBean(DataSource.class);
            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                // O Flyway aplicou a cadeia inteira, terminando na V12.
                try (ResultSet rs = stmt.executeQuery(
                        "select version, success from flyway_schema_history "
                                + "where version = '12'")) {
                    assertThat(rs.next()).as("a V12 precisa constar no histórico").isTrue();
                    assertThat(rs.getBoolean("success")).isTrue();
                }

                // E as tabelas existem de fato, com as colunas do modelo por períodos.
                try (ResultSet rs = stmt.executeQuery(
                        "select count(*) from information_schema.columns "
                                + "where table_name = 'business_hours' "
                                + "and column_name in ('business_id','dia_semana','hora_inicio','hora_fim')")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(4);
                }
                try (ResultSet rs = stmt.executeQuery(
                        "select count(*) from information_schema.columns "
                                + "where table_name = 'professional_availability' "
                                + "and column_name in ('business_id','professional_id','dia_semana',"
                                + "'hora_inicio','hora_fim','status')")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(6);
                }
            }
        }
    }

    @Test
    @DisplayName("expediente e disponibilidade SOBREVIVEM ao reinício, com os VOs intactos")
    void expedienteEDisponibilidadeSobrevivemAoReinicio() {
        BusinessId negocio = BusinessId.from(UUID.randomUUID());
        ProfessionalId idProfissional;

        // ---- Contexto A: grava ----
        try (ConfigurableApplicationContext ctxA = startContext()) {
            BusinessCalendarRepository expedientes = ctxA.getBean(BusinessCalendarRepository.class);
            AvailabilityRepository disponibilidades = ctxA.getBean(AvailabilityRepository.class);
            ProfessionalRepository profissionais = ctxA.getBean(ProfessionalRepository.class);
            BusinessRepository negocios = ctxA.getBean(BusinessRepository.class);

            // Os adapters de produção precisam ser os JPA, não os duplos em memória.
            assertThat(expedientes).isInstanceOf(JpaBusinessCalendarRepository.class);
            assertThat(disponibilidades).isInstanceOf(JpaAvailabilityRepository.class);

            negocios.save(new Business(negocio, "Negócio Teste", null, null));
            expedientes.salvar(new BusinessCalendar(negocio, expedienteComAlmoco()));

            Professional profissional = profissionais.salvar(new Professional(
                    ProfessionalId.generate(), negocio, "Profissional Teste",
                    Set.of(), Set.of(), "+5511999990000"));
            idProfissional = profissional.getId();

            disponibilidades.salvar(new Availability(AvailabilityId.generate(), negocio,
                    idProfissional, DiaSemana.SEGUNDA, periodo(9, 0, 12, 0)));
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), negocio,
                    idProfissional, DiaSemana.SEGUNDA, periodo(13, 0, 18, 0)));
            Availability inativa = new Availability(AvailabilityId.generate(), negocio,
                    idProfissional, DiaSemana.SABADO, periodo(9, 0, 14, 0));
            inativa.inativar();
            disponibilidades.salvar(inativa);
        }

        // ---- Contexto B: lê do zero, sem nada em memória ----
        try (ConfigurableApplicationContext ctxB = startContext()) {
            BusinessCalendarRepository expedientes = ctxB.getBean(BusinessCalendarRepository.class);
            AvailabilityRepository disponibilidades = ctxB.getBean(AvailabilityRepository.class);

            BusinessHours recarregado = expedientes.buscar(negocio).getExpediente();

            assertThat(expedientes.configurado(negocio)).isTrue();
            assertThat(recarregado.naoTemExpediente()).isFalse();
            // O ALMOÇO sobreviveu: dois períodos na segunda, na ordem certa.
            assertThat(recarregado.periodosDe(DiaSemana.SEGUNDA))
                    .containsExactly(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0));
            // O SÁBADO curto sobreviveu, diferente da segunda.
            assertThat(recarregado.periodosDe(DiaSemana.SABADO))
                    .containsExactly(periodo(9, 0, 14, 0));
            // DOMINGO continua fechado por AUSÊNCIA de linha.
            assertThat(recarregado.fechadoEm(DiaSemana.DOMINGO)).isTrue();

            // Disponibilidade: os VOs voltam inteiros, e o status é preservado.
            List<Availability> daSegunda = disponibilidades.listarAtivasPorProfissionalEDia(
                    negocio, idProfissional, DiaSemana.SEGUNDA);
            assertThat(daSegunda).hasSize(2);
            assertThat(daSegunda).allSatisfy(a -> {
                assertThat(a.getBusinessId()).isEqualTo(negocio);
                assertThat(a.getProfessionalId()).isEqualTo(idProfissional);
                assertThat(a.getStatus()).isEqualTo(AvailabilityStatus.ATIVO);
                assertThat(a.getCriadoEm()).isNotNull();
                assertThat(a.getAtualizadoEm()).isNotNull();
            });
            assertThat(daSegunda).extracting(Availability::getPeriodo)
                    .containsExactlyInAnyOrder(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0));

            // A INATIVA não aparece na consulta de horários, mas continua persistida.
            assertThat(disponibilidades.listarAtivasPorProfissionalEDia(
                    negocio, idProfissional, DiaSemana.SABADO)).isEmpty();
            assertThat(disponibilidades.listarPorProfissional(negocio, idProfissional)).hasSize(3);
        }
    }

    @Test
    @DisplayName("salvar o expediente SUBSTITUI o anterior, sem empilhar períodos")
    void salvarSubstituiExpediente() {
        BusinessId negocio = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessCalendarRepository expedientes = ctx.getBean(BusinessCalendarRepository.class);
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            negocios.save(new Business(negocio, "Negócio Teste", null, null));

            expedientes.salvar(new BusinessCalendar(negocio, expedienteComAlmoco()));
            expedientes.salvar(new BusinessCalendar(negocio, BusinessHours.deSemana(
                    Map.of(DiaSemana.TERCA, List.of(periodo(10, 0, 19, 0))))));

            BusinessHours atual = expedientes.buscar(negocio).getExpediente();
            assertThat(atual.getDiasFuncionamento()).containsExactly(DiaSemana.TERCA);
            assertThat(atual.fechadoEm(DiaSemana.SEGUNDA)).isTrue();
        }
    }

    @Test
    @DisplayName("negócio sem expediente devolve o estado NÃO CONFIGURADO, nunca null")
    void semExpedienteDevolveNaoConfigurado() {
        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessCalendarRepository expedientes = ctx.getBean(BusinessCalendarRepository.class);
            BusinessId virgem = BusinessId.from(UUID.randomUUID());

            assertThat(expedientes.configurado(virgem)).isFalse();
            assertThat(expedientes.buscar(virgem).getExpediente().naoTemExpediente()).isTrue();
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa disponibilidade do profissional de A gravada sob o negócio B")
    void bancoRecusaDisponibilidadeCruzada() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            ProfessionalRepository profissionais = ctx.getBean(ProfessionalRepository.class);
            AvailabilityRepository disponibilidades = ctx.getBean(AvailabilityRepository.class);
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            // NEGOCIO_B precisa existir como Business para que a FK violada pelo insert
            // cruzado seja a composta (business_id, professional_id) -> professionals, e não
            // a de business_id -> businesses — é essa a invariante que este teste prova.
            negocios.save(new Business(NEGOCIO_B, "Negócio B de Teste", null, null));

            Professional doA = profissionais.salvar(new Professional(ProfessionalId.generate(),
                    NEGOCIO_A, "Profissional do A", Set.of(), Set.of(), "+5511999990001"));

            DataSource dataSource = ctx.getBean(DataSource.class);
            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                // Contorna TODA a camada Java: (business_id = B, professional_id = do A) não
                // satisfaz a FK composta (business_id, professional_id) -> professionals.
                String sqlCruzado = String.format(
                        "insert into professional_availability "
                                + "(id, business_id, professional_id, dia_semana, hora_inicio, hora_fim, "
                                + " status, criado_em, atualizado_em) "
                                + "values ('%s','%s','%s','SEGUNDA','09:00','12:00','ATIVO', now(), now())",
                        UUID.randomUUID(), NEGOCIO_B.getValue(), doA.getId().getValue());

                assertThatThrownBy(() -> stmt.executeUpdate(sqlCruzado))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("fk_professional_availability_professional");
            }

            // E a associação legítima, no MESMO negócio, continua funcionando.
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), NEGOCIO_A,
                    doA.getId(), DiaSemana.SEGUNDA, periodo(9, 0, 12, 0)));
            assertThat(disponibilidades.listarAtivasPorProfissionalEDia(
                    NEGOCIO_A, doA.getId(), DiaSemana.SEGUNDA)).hasSize(1);
        }
    }

    @Test
    @DisplayName("o PostgreSQL recusa período invertido, mesmo por carga direta")
    void bancoRecusaPeriodoInvertido() throws SQLException {
        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessId negocio = BusinessId.from(UUID.randomUUID());
            ctx.getBean(BusinessRepository.class).save(new Business(negocio, "Negócio Teste", null, null));
            DataSource dataSource = ctx.getBean(DataSource.class);

            try (Connection conexao = dataSource.getConnection();
                 Statement stmt = conexao.createStatement()) {

                String sqlInvertido = String.format(
                        "insert into business_hours "
                                + "(id, business_id, dia_semana, hora_inicio, hora_fim, criado_em, atualizado_em) "
                                + "values ('%s','%s','SEGUNDA','18:00','09:00', now(), now())",
                        UUID.randomUUID(), negocio.getValue());

                assertThatThrownBy(() -> stmt.executeUpdate(sqlInvertido))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ck_business_hours_periodo_valido");
            }
        }
    }

    @Test
    @DisplayName("dois negócios mantêm expedientes distintos no mesmo banco")
    void doisNegociosExpedientesDistintos() {
        BusinessId a = BusinessId.from(UUID.randomUUID());
        BusinessId b = BusinessId.from(UUID.randomUUID());

        try (ConfigurableApplicationContext ctx = startContext()) {
            BusinessCalendarRepository expedientes = ctx.getBean(BusinessCalendarRepository.class);
            BusinessRepository negocios = ctx.getBean(BusinessRepository.class);
            negocios.save(new Business(a, "Negócio A de Teste", null, null));
            negocios.save(new Business(b, "Negócio B de Teste", null, null));

            expedientes.salvar(new BusinessCalendar(a, expedienteComAlmoco()));
            expedientes.salvar(new BusinessCalendar(b, BusinessHours.deSemana(
                    Map.of(DiaSemana.SEGUNDA, List.of(periodo(14, 0, 20, 0))))));

            List<IntervaloDeHorario> segundaDeA = new ArrayList<>(
                    expedientes.buscar(a).getExpediente().periodosDe(DiaSemana.SEGUNDA));
            List<IntervaloDeHorario> segundaDeB = new ArrayList<>(
                    expedientes.buscar(b).getExpediente().periodosDe(DiaSemana.SEGUNDA));

            assertThat(segundaDeA).containsExactly(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0));
            assertThat(segundaDeB).containsExactly(periodo(14, 0, 20, 0));
            assertThat(expedientes.buscar(b).getExpediente().fechadoEm(DiaSemana.SABADO)).isTrue();
            assertThat(expedientes.buscar(a).getExpediente().fechadoEm(DiaSemana.SABADO)).isFalse();
        }
    }
}
