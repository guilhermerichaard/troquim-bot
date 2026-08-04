package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.BusinessHoursRepository;
import com.troquim_bot.schedule.ScheduleService;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.CatalogoDeTeste;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.flow.application.availability.FlowAvailabilityQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O WhatsApp Flow lendo a MESMA fonte oficial: o expediente persistido.
 *
 * A agenda global e em memória do {@code ScheduleService} deixou de alimentar este caminho.
 * Aqui isso é provado pelo comportamento, e não só pela ausência de import: mexer no
 * gabarito legado não muda uma linha do que o Flow oferece.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("WhatsApp Flow - horários vêm do expediente persistido")
class FlowExpedientePersistidoTest {

    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
    }

    @Autowired
    private FlowAvailabilityQuery disponibilidadeDoFlow;
    @Autowired
    private ProvisionarNegocio provisionarNegocio;
    @Autowired
    private ConsultarCatalogo consultarCatalogo;
    @Autowired
    private BusinessHoursRepository expedientes;
    @Autowired
    private ScheduleService scheduleServiceLegado;
    @Autowired
    private AvailabilityApplicationService availabilityApplicationService;

    private ServiceId unhas;
    private ProfessionalId profissional;

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    /** Próxima quarta-feira: dia útil em qualquer execução. */
    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    @BeforeEach
    void provisionar() {
        CatalogoDeTeste.provisionar(provisionarNegocio, TestTenants.PILOT);
        var item = CatalogoDeTeste.item(consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS);
        unhas = item.id();
        profissional = item.profissionais().get(0).id();
    }

    @Test
    @DisplayName("35. os horários do Flow saem do expediente PERSISTIDO — mudar o expediente muda a lista")
    void horariosVemDoExpedientePersistido() {
        LocalDate quarta = proximaQuarta();

        List<LocalTime> comExpedientePadrao = disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, quarta, profissional);
        assertThat(comExpedientePadrao).isNotEmpty();
        assertThat(comExpedientePadrao).startsWith(LocalTime.of(9, 0));

        // O dono reduz a quarta-feira para o fim da tarde.
        Map<DiaSemana, List<IntervaloDeHorario>> novaSemana =
                new EnumMap<>(CatalogoDeTeste.expedientePadrao().porDiaDaSemana());
        novaSemana.put(DiaSemana.QUARTA, List.of(periodo(16, 0, 18, 0)));
        expedientes.salvar(TestTenants.PILOT, BusinessHours.deSemana(novaSemana));

        List<LocalTime> depois = disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, quarta, profissional);

        assertThat(depois).isNotEmpty();
        assertThat(depois).doesNotContain(LocalTime.of(9, 0), LocalTime.of(13, 0));
        assertThat(depois).startsWith(LocalTime.of(16, 0));

        // Restaura para não afetar outros testes do mesmo contexto.
        expedientes.salvar(TestTenants.PILOT, CatalogoDeTeste.expedientePadrao());
    }

    @Test
    @DisplayName("35b. o INTERVALO DE ALMOÇO aparece no Flow: nada entre 12:00 e 13:00")
    void almocoChegaAoFlow() {
        List<LocalTime> horarios = disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, proximaQuarta(), profissional);

        assertThat(horarios).contains(LocalTime.of(11, 0));
        assertThat(horarios).doesNotContain(LocalTime.of(12, 0), LocalTime.of(12, 30));
        assertThat(horarios).contains(LocalTime.of(13, 0));
    }

    @Test
    @DisplayName("36. o Flow de um negócio não recebe horários de outro")
    void naoRecebeHorariosDeOutroNegocio() {
        // Um negócio sem catálogo e sem expediente algum.
        BusinessId virgem = BusinessId.from(UUID.randomUUID());

        assertThat(disponibilidadeDoFlow.horariosLivres(virgem, unhas, proximaQuarta(), profissional))
                .as("serviço e profissional são de OUTRO negócio")
                .isEmpty();
        assertThat(disponibilidadeDoFlow.datasDisponiveis(virgem, unhas,
                LocalDate.now(), LocalDate.now().plusDays(30), profissional)).isEmpty();
    }

    @Test
    @DisplayName("37. o Flow usa a duração REAL do ServiceId selecionado")
    void usaDuracaoRealDoServico() {
        // Dois serviços do MESMO negócio com durações diferentes, mesmo profissional e dia.
        BusinessId tenant = BusinessId.from(UUID.randomUUID());
        provisionarNegocio.provisionar(tenant,
                List.of(new ProvisionarNegocio.ServicoDesejado("Curto", 30),
                        new ProvisionarNegocio.ServicoDesejado("Longo", 180)),
                new ProvisionarNegocio.ProfissionalDesejado("Profissional", "+5511900000001",
                        List.of("Curto", "Longo"),
                        Map.of(DiaSemana.QUARTA, List.of(periodo(9, 0, 12, 0)))),
                BusinessHours.deSemana(Map.of(DiaSemana.QUARTA, List.of(periodo(9, 0, 12, 0)))));

        var curto = CatalogoDeTeste.item(consultarCatalogo, tenant, "Curto");
        var longo = CatalogoDeTeste.item(consultarCatalogo, tenant, "Longo");
        LocalDate quarta = proximaQuarta();

        List<LocalTime> doCurto = disponibilidadeDoFlow.horariosLivres(
                tenant, curto.id(), quarta, curto.profissionais().get(0).id());
        List<LocalTime> doLongo = disponibilidadeDoFlow.horariosLivres(
                tenant, longo.id(), quarta, longo.profissionais().get(0).id());

        // 30min no período 09:00-12:00 vai até 11:30; 3h só cabe começando às 09:00.
        assertThat(doCurto).endsWith(LocalTime.of(11, 30));
        assertThat(doLongo).containsExactly(LocalTime.of(9, 0));
        assertThat(doCurto).isNotEqualTo(doLongo);
    }

    @Test
    @DisplayName("38. FlowAvailabilityQuery não tem calendário nem regra própria")
    void adapterNaoTemCalendarioProprio() {
        Field[] campos = FlowAvailabilityQuery.class.getDeclaredFields();

        // Nenhum estado estático: sem lista de horários, sem mapa de agenda.
        for (Field campo : campos) {
            if (Modifier.isStatic(campo.getModifiers())) {
                assertThat(Collection.class.isAssignableFrom(campo.getType())
                        || Map.class.isAssignableFrom(campo.getType())
                        || campo.getType().isArray()
                        || campo.getType() == java.time.Duration.class
                        || campo.getType() == LocalTime.class)
                        .as("campo estático '%s' parece agenda ou duração fixa", campo.getName())
                        .isFalse();
            }
        }

        // A ÚNICA dependência é a fronteira oficial: nada de repositório, relógio ou gabarito.
        assertThat(campos).hasSize(1);
        assertThat(campos[0].getType()).isEqualTo(AvailabilityApplicationService.class);
    }

    @Test
    @DisplayName("39. mexer no ScheduleService legado NÃO muda o que o Flow oferece")
    void scheduleServiceNaoAlimentaOFlow() {
        LocalDate quarta = proximaQuarta();
        List<LocalTime> antes = disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, quarta, profissional);

        // Bloqueia horários no gabarito global antigo. Se o Flow ainda o lesse, a lista mudaria.
        assertThat(scheduleServiceLegado.bloquearHorario("quarta", "09:00", "teste")).isTrue();
        assertThat(scheduleServiceLegado.reservarHorario("quarta", "10:00", "5511999999999")).isTrue();

        List<LocalTime> depois = disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, quarta, profissional);

        assertThat(depois).isEqualTo(antes);
        assertThat(depois).contains(LocalTime.of(9, 0), LocalTime.of(10, 0));

        // Devolve o gabarito legado ao estado anterior para não contaminar o contexto.
        scheduleServiceLegado.liberarHorario("quarta", "09:00");
        scheduleServiceLegado.cancelarReserva("quarta", "10:00");
    }

    @Test
    @DisplayName("40. o caminho LEGADO da conversa segue lendo o gabarito antigo, sem conversão silenciosa")
    void caminhoLegadoContinuaComoAntes() {
        // O menu textual continua respondendo de hora em hora, como sempre respondeu —
        // e NÃO passa a devolver os slots de 15 em 15 do caminho novo.
        @SuppressWarnings("deprecation")
        List<String> legado = availabilityApplicationService.consultarDisponibilidade("quarta");

        assertThat(legado).isNotEmpty();
        assertThat(legado).contains("09:00", "10:00");
        assertThat(legado).doesNotContain("09:15", "09:30", "09:45");

        // E o caminho novo, no mesmo dia, oferece a granularidade de 15 minutos.
        assertThat(disponibilidadeDoFlow.horariosLivres(
                TestTenants.PILOT, unhas, proximaQuarta(), profissional))
                .contains(LocalTime.of(9, 15));
    }
}
