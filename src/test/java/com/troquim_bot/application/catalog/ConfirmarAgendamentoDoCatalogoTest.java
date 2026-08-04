package com.troquim_bot.application.catalog;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.ConsultarDisponibilidade;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIds;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryBusinessCalendarRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O caso de uso oficial de confirmação a partir do catálogo persistido.
 *
 * Todas as regras que antes estariam espalhadas (ou ausentes) vivem aqui e são provadas
 * aqui: tenant, serviço ativo, profissional ativo e habilitado — e, acima de tudo, que o
 * {@code ServiceId} do catálogo chega intacto ao agendamento.
 */
@DisplayName("ConfirmarAgendamentoDoCatalogo - catálogo decide, e o id é preservado")
class ConfirmarAgendamentoDoCatalogoTest {

    private static final BusinessId SALAO = TestTenants.PILOT;
    private static final BusinessId OUTRO_SALAO = TestTenants.OUTRO;

    private static final String TELEFONE = "5511999990000";
    private static final String NOME = "Ana Souza";

    private InMemoryServiceRepository servicos;
    private InMemoryProfessionalRepository profissionais;
    private InMemoryAppointmentRepository appointments;
    private AppointmentApplicationService appointmentApplicationService;
    private ConfirmarAgendamentoDoCatalogo confirmar;

    private Service unhas;
    private Professional malu;

    @BeforeEach
    void montarCatalogoEAplicacao() {
        servicos = new InMemoryServiceRepository();
        profissionais = new InMemoryProfessionalRepository();
        appointments = new InMemoryAppointmentRepository();

        InMemoryReservationRepository reservas = new InMemoryReservationRepository();
        appointmentApplicationService = new AppointmentApplicationService(appointments, reservas);

        // Calendário e disponibilidade REAIS: o caminho tipado agora revalida o slot via
        // ConsultarDisponibilidade dentro da seção crítica, então "Aceito" só confirma se
        // expediente e disponibilidade cobrirem o horário do pedido. Janela larga em TODOS
        // os dias da semana para não depender de em qual dia o teste roda.
        InMemoryBusinessCalendarRepository calendarios = new InMemoryBusinessCalendarRepository();
        InMemoryAvailabilityRepository disponibilidades = new InMemoryAvailabilityRepository();
        IntervaloDeHorario janelaAmpla = IntervaloDeHorario.de(LocalTime.of(7, 0), LocalTime.of(21, 0));
        Map<DiaSemana, List<IntervaloDeHorario>> semanaAberta = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : DiaSemana.values()) {
            semanaAberta.put(dia, List.of(janelaAmpla));
        }
        calendarios.salvar(new BusinessCalendar(SALAO, BusinessHours.deSemana(semanaAberta)));

        ConsultarDisponibilidade consultarDisponibilidade = new ConsultarDisponibilidade(
                new ConsultarCatalogo(servicos, profissionais), calendarios, disponibilidades,
                appointments, new RelogioDoNegocio());

        BookingApplicationService booking = new BookingApplicationService(
                TestTenants.of(SALAO),
                new ReservationApplicationService(reservas),
                appointmentApplicationService,
                new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.of(SALAO)),
                new InMemoryBookingIdempotencyStore(),
                new com.troquim_bot.infrastructure.persistence.InMemoryBookingSlotCriticalSection(),
                consultarDisponibilidade);

        confirmar = new ConfirmarAgendamentoDoCatalogo(
                new ConsultarCatalogo(servicos, profissionais), booking);

        unhas = servicos.salvar(Service.novoSemPreco(ServiceId.generate(), SALAO, "Unhas", null,
                ServiceDuration.ofMinutes(60)));
        malu = profissionais.salvar(new Professional(ProfessionalId.generate(), SALAO, "Malu",
                Set.of(unhas.getId()), Set.of(), "+5511900000000"));

        for (DiaSemana dia : DiaSemana.values()) {
            disponibilidades.salvar(new com.troquim_bot.availability.Availability(
                    com.troquim_bot.availability.AvailabilityId.generate(), SALAO, malu.getId(), dia, janelaAmpla));
        }
    }

    private ConfirmarAgendamentoDoCatalogo.Pedido pedido(BusinessId tenant, ServiceId servico,
                                                          ProfessionalId profissional) {
        LocalDate data = LocalDate.now().plusDays(2);
        LocalTime horario = LocalTime.of(10, 0);
        return new ConfirmarAgendamentoDoCatalogo.Pedido(tenant, servico, profissional,
                TELEFONE, NOME, data, horario,
                BookingCommandKey.de("token-de-teste-" + UUID.randomUUID(), tenant.getValue(),
                        TELEFONE, servico, profissional, data, horario));
    }

    private ConfirmarAgendamentoDoCatalogo.Pedido pedidoValido() {
        return pedido(SALAO, unhas.getId(), malu.getId());
    }

    @Nested
    @DisplayName("Caminho aceito")
    class Aceito {

        @Test
        @DisplayName("agenda e grava EXATAMENTE o ServiceId do catálogo")
        void agendaComOServiceIdDoCatalogo() {
            ConfirmarAgendamentoDoCatalogo.Resultado resultado = confirmar.confirmar(pedidoValido());

            assertThat(resultado.foiRecusado()).isFalse();
            assertThat(resultado.agendamento().orElseThrow().isConfirmado()).isTrue();

            List<Appointment> criados = appointments.findAll();
            assertThat(criados).hasSize(1);
            assertThat(criados.get(0).getServiceId()).isEqualTo(unhas.getId());
            assertThat(criados.get(0).getProfessionalId()).isEqualTo(malu.getId());
        }

        @Test
        @DisplayName("o id gravado NÃO é derivado do UUID textual nem do nome")
        void naoDerivaIdDeTexto() {
            confirmar.confirmar(pedidoValido());

            ServiceId gravado = appointments.findAll().get(0).getServiceId();
            assertThat(gravado).isNotEqualTo(BookingIds.serviceId(unhas.getId().getValue().toString()));
            assertThat(gravado).isNotEqualTo(BookingIds.serviceId("Unhas"));
            assertThat(gravado).isNotEqualTo(BookingIds.serviceId("unha"));
        }
    }

    @Nested
    @DisplayName("Violações de catálogo — erro controlado, nada escrito")
    class Recusas {

        @Test
        @DisplayName("serviço de outro tenant é rejeitado")
        void servicoDeOutroTenant() {
            var resultado = confirmar.confirmar(pedido(OUTRO_SALAO, unhas.getId(), malu.getId()));

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.SERVICO_INDISPONIVEL);
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("serviço inativo é rejeitado na confirmação")
        void servicoInativo() {
            unhas.desativar();
            servicos.salvar(unhas);

            var resultado = confirmar.confirmar(pedidoValido());

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.SERVICO_INDISPONIVEL);
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("serviço inexistente é rejeitado, sem inventar id")
        void servicoInexistente() {
            var resultado = confirmar.confirmar(pedido(SALAO, ServiceId.generate(), malu.getId()));

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.SERVICO_INDISPONIVEL);
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("profissional de outro tenant é rejeitado")
        void profissionalDeOutroTenant() {
            Professional intruso = profissionais.salvar(new Professional(ProfessionalId.generate(),
                    OUTRO_SALAO, "Rui", Set.of(unhas.getId()), Set.of(), "+5511911111111"));

            var resultado = confirmar.confirmar(pedido(SALAO, unhas.getId(), intruso.getId()));

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.PROFISSIONAL_INDISPONIVEL);
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("profissional inativo é rejeitado, mesmo habilitado")
        void profissionalInativo() {
            malu.desativar();
            profissionais.salvar(malu);

            var resultado = confirmar.confirmar(pedidoValido());

            // Sem ninguém ativo, o serviço deixa de ser ofertável: a recusa vem antes.
            assertThat(resultado.foiRecusado()).isTrue();
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("profissional não habilitado para o serviço é rejeitado")
        void profissionalNaoHabilitado() {
            Professional rita = profissionais.salvar(new Professional(ProfessionalId.generate(),
                    SALAO, "Rita", Set.of(), Set.of("unhas"), "+5511922222222"));

            var resultado = confirmar.confirmar(pedido(SALAO, unhas.getId(), rita.getId()));

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.PROFISSIONAL_INDISPONIVEL);
            assertThat(appointments.findAll()).isEmpty();
        }

        @Test
        @DisplayName("especialidade textual não habilita: o nome bate e mesmo assim recusa")
        void especialidadeTextualNaoHabilita() {
            Professional soTexto = profissionais.salvar(new Professional(ProfessionalId.generate(),
                    SALAO, "Bia", Set.of(), Set.of("Unhas"), "+5511933333333"));

            var resultado = confirmar.confirmar(pedido(SALAO, unhas.getId(), soTexto.getId()));

            assertThat(resultado.recusa())
                    .contains(ConfirmarAgendamentoDoCatalogo.Recusa.PROFISSIONAL_INDISPONIVEL);
        }
    }

    @Nested
    @DisplayName("Tenant obrigatório")
    class Tenant {

        @Test
        @DisplayName("BusinessId nulo é recusado — não existe confirmação sem tenant")
        void tenantObrigatorio() {
            LocalDate data = LocalDate.now().plusDays(2);
            LocalTime horario = LocalTime.of(10, 0);
            var semTenant = new ConfirmarAgendamentoDoCatalogo.Pedido(
                    null, unhas.getId(), malu.getId(), TELEFONE, NOME, data, horario,
                    BookingCommandKey.de("token", SALAO.getValue(), TELEFONE,
                            unhas.getId(), malu.getId(), data, horario));

            assertThatThrownBy(() -> confirmar.confirmar(semTenant))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BusinessId");
        }

        @Test
        @DisplayName("ServiceId nulo é recusado — nada é derivado de texto neste caminho")
        void servicoObrigatorio() {
            LocalDate data = LocalDate.now().plusDays(2);
            LocalTime horario = LocalTime.of(10, 0);
            var semServico = new ConfirmarAgendamentoDoCatalogo.Pedido(
                    SALAO, null, malu.getId(), TELEFONE, NOME, data, horario,
                    BookingCommandKey.de("token", SALAO.getValue(), TELEFONE,
                            unhas.getId(), malu.getId(), data, horario));

            assertThatThrownBy(() -> confirmar.confirmar(semServico))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ServiceId");
        }
    }
}
