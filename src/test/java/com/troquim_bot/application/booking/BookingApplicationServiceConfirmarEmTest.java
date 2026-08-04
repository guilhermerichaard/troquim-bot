package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.ConsultarDisponibilidade;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
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
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code confirmarEm} (caminho tipado) — tenant SEMPRE explícito da
 * {@link BookingCommandKey}, nunca do {@link com.troquim_bot.business.TenantProvider}, e
 * duração sem fallback.
 */
@DisplayName("BookingApplicationService.confirmarEm - tenant explícito e duração sem fallback")
class BookingApplicationServiceConfirmarEmTest {

    private static final BusinessId CHAVE_TENANT = TestTenants.PILOT;
    private static final BusinessId TENANT_PROVIDER_TENANT = TestTenants.OUTRO;

    private InMemoryAppointmentRepository appointments;
    private InMemoryReservationRepository reservations;
    private InMemoryCustomerRepository customers;
    private BookingApplicationService booking;

    private ServiceId servicoId;
    private ProfessionalId profissionalId;

    @BeforeEach
    void montar() {
        InMemoryServiceRepository servicos = new InMemoryServiceRepository();
        InMemoryProfessionalRepository profissionais = new InMemoryProfessionalRepository();
        appointments = new InMemoryAppointmentRepository();
        reservations = new InMemoryReservationRepository();
        customers = new InMemoryCustomerRepository();
        InMemoryBusinessCalendarRepository calendarios = new InMemoryBusinessCalendarRepository();
        InMemoryAvailabilityRepository disponibilidades = new InMemoryAvailabilityRepository();

        servicoId = ServiceId.generate();
        profissionalId = ProfessionalId.generate();
        servicos.salvar(Service.novoSemPreco(servicoId, CHAVE_TENANT, "Corte", null,
                ServiceDuration.ofMinutes(60)));
        profissionais.salvar(new Professional(profissionalId, CHAVE_TENANT, "Profissional",
                Set.of(servicoId), Set.of(), "+5511900000000"));

        IntervaloDeHorario janela = IntervaloDeHorario.de(LocalTime.of(7, 0), LocalTime.of(21, 0));
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new java.util.EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : DiaSemana.values()) {
            semana.put(dia, List.of(janela));
        }
        calendarios.salvar(new BusinessCalendar(CHAVE_TENANT, BusinessHours.deSemana(semana)));
        for (DiaSemana dia : DiaSemana.values()) {
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), CHAVE_TENANT,
                    profissionalId, dia, janela));
        }

        ConsultarDisponibilidade consultarDisponibilidade = new ConsultarDisponibilidade(
                new ConsultarCatalogo(servicos, profissionais), calendarios, disponibilidades,
                appointments, new RelogioDoNegocio());

        // TenantProvider aponta para OUTRO negócio — a prova é que confirmarEm IGNORA isso.
        booking = new BookingApplicationService(
                TestTenants.of(TENANT_PROVIDER_TENANT),
                new ReservationApplicationService(reservations),
                new AppointmentApplicationService(appointments, reservations),
                new CustomerProfileService(customers, TestTenants.of(TENANT_PROVIDER_TENANT)),
                new com.troquim_bot.support.InMemoryBookingIdempotencyStore(),
                new com.troquim_bot.infrastructure.persistence.InMemoryBookingSlotCriticalSection(),
                consultarDisponibilidade);
    }

    private LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private BookingCommandKey chave(String base, String telefone, LocalDate data, LocalTime hora) {
        return BookingCommandKey.de(base, CHAVE_TENANT.getValue(), telefone, servicoId, profissionalId, data, hora);
    }

    @Test
    @DisplayName("1. persiste o Customer no BusinessId da chave, mesmo com TenantProvider apontando para outro tenant")
    void persisteCustomerNoBusinessIdDaChave() {
        String telefone = "5511999990001";
        LocalDate data = proximaQuarta();
        LocalTime hora = LocalTime.of(10, 0);

        BookingResult resultado = booking.confirmarEm(telefone, "Ana Souza", servicoId, "Corte",
                profissionalId, data, hora, Duration.ofMinutes(60),
                chave("cmd-tenant", telefone, data, hora));

        assertThat(resultado.isConfirmado()).isTrue();
        assertThat(customers.findByBusinessAndPhone(CHAVE_TENANT,
                new com.troquim_bot.common.valueobject.PhoneNumber(telefone))).isPresent();
        assertThat(customers.findByBusinessAndPhone(TENANT_PROVIDER_TENANT,
                new com.troquim_bot.common.valueobject.PhoneNumber(telefone))).isEmpty();
    }

    @Test
    @DisplayName("2. Reservation e Appointment usam o MESMO BusinessId explícito da chave")
    void reservationEAppointmentUsamOBusinessIdDaChave() {
        String telefone = "5511999990002";
        LocalDate data = proximaQuarta();
        LocalTime hora = LocalTime.of(11, 0);

        booking.confirmarEm(telefone, "Bia Lima", servicoId, "Corte", profissionalId, data, hora,
                Duration.ofMinutes(60), chave("cmd-tenant-2", telefone, data, hora));

        assertThat(appointments.findAll()).hasSize(1);
        assertThat(appointments.findAll().get(0).getBusinessId()).isEqualTo(CHAVE_TENANT);

        List<Reservation> doTenantDaChave = reservations.findByBusinessId(CHAVE_TENANT);
        assertThat(doTenantDaChave).hasSize(1);
        assertThat(reservations.findByBusinessId(TENANT_PROVIDER_TENANT)).isEmpty();
    }

    @Test
    @DisplayName("13. duração nula é recusada, sem fallback")
    void duracaoNulaEhRecusada() {
        String telefone = "5511999990003";
        LocalDate data = proximaQuarta();
        LocalTime hora = LocalTime.of(12, 0);

        assertThatThrownBy(() -> booking.confirmarEm(telefone, "Cliente", servicoId, "Corte",
                profissionalId, data, hora, null, chave("cmd-sem-duracao", telefone, data, hora)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duracao");

        assertThat(appointments.findAll()).isEmpty();
    }

    @Test
    @DisplayName("13b. duração zero ou negativa é recusada")
    void duracaoZeroOuNegativaEhRecusada() {
        String telefone = "5511999990004";
        LocalDate data = proximaQuarta();
        LocalTime hora = LocalTime.of(14, 0);

        assertThatThrownBy(() -> booking.confirmarEm(telefone, "Cliente", servicoId, "Corte",
                profissionalId, data, hora, Duration.ZERO, chave("cmd-zero", telefone, data, hora)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> booking.confirmarEm(telefone, "Cliente", servicoId, "Corte",
                profissionalId, data, hora, Duration.ofMinutes(-10),
                chave("cmd-negativo", telefone, data, hora)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("14. ServiceId de outro tenant é recusado: a checagem de disponibilidade real barra, sem criar nada")
    void servicoDeOutroTenantEhRecusado() {
        ServiceId servicoDeOutroTenant = ServiceId.generate();
        String telefone = "5511999990005";
        LocalDate data = proximaQuarta();
        LocalTime hora = LocalTime.of(15, 0);

        BookingCommandKey chave = BookingCommandKey.de("cmd-outro-tenant", CHAVE_TENANT.getValue(),
                telefone, servicoDeOutroTenant, profissionalId, data, hora);

        BookingResult resultado = booking.confirmarEm(telefone, "Cliente", servicoDeOutroTenant, "Corte",
                profissionalId, data, hora, Duration.ofMinutes(60), chave);

        assertThat(resultado.status()).isEqualTo(BookingResult.Status.INDISPONIVEL);
        assertThat(appointments.findAll()).isEmpty();
    }
}
