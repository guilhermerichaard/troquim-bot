package com.troquim_bot.owner.application;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.owner.OwnerUserId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OwnerConsoleCommandServiceTest {

    private AppointmentApplicationService appointments;
    private ServiceApplicationService services;
    private ProfessionalApplicationService professionals;
    private OwnerConsoleCommandService commands;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentApplicationService.class);
        services = mock(ServiceApplicationService.class);
        professionals = mock(ProfessionalApplicationService.class);
        commands = new OwnerConsoleCommandService(
                appointments,
                mock(AvailabilityApplicationService.class),
                mock(ConfirmarAgendamentoDoCatalogo.class),
                mock(ConsultarCatalogo.class),
                mock(CustomerApplicationService.class),
                services,
                professionals);
    }

    @Test
    void ownerNaoPodeCancelarAppointmentDeOutroTenant() {
        BusinessId ownerBusiness = BusinessId.from(java.util.UUID.randomUUID());
        BusinessId foreignBusiness = BusinessId.from(java.util.UUID.randomUUID());
        AppointmentId id = AppointmentId.generate();

        Appointment foreign = new Appointment(
                id,
                foreignBusiness,
                CustomerId.generate(),
                ProfessionalId.generate(),
                ServiceId.generate(),
                AvailabilityId.generate(),
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        when(appointments.buscarPorId(id)).thenReturn(Optional.of(foreign));

        AuthenticatedOwner owner = new AuthenticatedOwner(OwnerUserId.generate(), ownerBusiness);

        assertThrows(IllegalArgumentException.class,
                () -> commands.cancelAppointment(owner, id.getValue().toString()));

        verify(appointments, never()).cancelarAgendamento(any());
    }

    @Test
    void updateServiceUsaTenantDaSessaoEmTodasAsMutacoes() {
        BusinessId business = BusinessId.from(java.util.UUID.randomUUID());
        ServiceId serviceId = ServiceId.generate();
        AuthenticatedOwner owner = new AuthenticatedOwner(OwnerUserId.generate(), business);

        var command = new OwnerConsoleCommandService.ServiceCommand(
                "Corte", "Corte masculino", 45, 55.0);

        commands.updateService(owner, serviceId.getValue().toString(), command);

        verify(services).atualizarNome(business, serviceId, "Corte");
        verify(services).atualizarDescricao(business, serviceId, "Corte masculino");
        verify(services).atualizarDuracao(business, serviceId, 45);
        verify(services).atualizarPreco(eq(business), eq(serviceId), any());
        verifyNoMoreInteractions(professionals);
    }
}
