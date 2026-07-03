package com.troquim_bot.application.appointment;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.service.ServiceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentApplicationServiceTest {

    private AppointmentApplicationService appointmentApplicationService;
    private InMemoryAppointmentRepository appointmentRepository;
    private InMemoryReservationRepository reservationRepository;

    private final CustomerId customerId1 = CustomerId.from(UUID.randomUUID());
    private final CustomerId customerId2 = CustomerId.from(UUID.randomUUID());
    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ProfessionalId profId2 = ProfessionalId.from(UUID.randomUUID());
    private final ServiceId serviceId1 = ServiceId.from(UUID.randomUUID());
    private final AvailabilityId availabilityId1 = AvailabilityId.from(UUID.randomUUID());

    private final LocalDate futureDate = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        appointmentRepository = new InMemoryAppointmentRepository();
        reservationRepository = new InMemoryReservationRepository();
        appointmentApplicationService = new AppointmentApplicationService(appointmentRepository, reservationRepository);
    }

    // ==================== criarAgendamento ====================

    @Test
    void deveCriarAgendamentoComSucesso() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertNotNull(appointment);
        assertNotNull(appointment.getId());
        assertEquals(customerId1, appointment.getCustomerId());
        assertEquals(profId1, appointment.getProfessionalId());
        assertEquals(serviceId1, appointment.getServiceId());
        assertEquals(availabilityId1, appointment.getAvailabilityId());
        assertNull(appointment.getReservationId());
        assertEquals(futureDate, appointment.getDate());
        assertEquals(LocalTime.of(8, 0), appointment.getStartTime());
        assertEquals(LocalTime.of(9, 0), appointment.getEndTime());
        assertEquals(AppointmentStatus.PENDENTE, appointment.getStatus());
        assertTrue(appointment.isAtivo());
        assertTrue(appointment.podeConfirmar());
        assertNotNull(appointment.getCriadoEm());
        assertNotNull(appointment.getAtualizadoEm());
    }

    @Test
    void deveLancarExcecaoQuandoCustomerIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                null, profId1, serviceId1, availabilityId1,
                futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoProfessionalIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, null, serviceId1, availabilityId1,
                futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoServiceIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, null, availabilityId1,
                futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoAvailabilityIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, null,
                futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoDataNula() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                null, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                futureDate, null, LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoEndTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                futureDate, LocalTime.of(8, 0), null));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeMaiorQueEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                futureDate, LocalTime.of(10, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeIgualEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                futureDate, LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoDataNoPassado() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId1, profId1, serviceId1, availabilityId1,
                pastDate, LocalTime.of(8, 0), LocalTime.of(9, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoHorarioConflitante() {
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamento(
                customerId2, profId1, serviceId1, availabilityId1,
                futureDate, LocalTime.of(9, 0), LocalTime.of(11, 0)));
    }

    @Test
    void devePermitirHorarioNaoConflitante() {
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(10, 0));

        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(10, 0), LocalTime.of(12, 0));

        assertNotNull(appointment);
        assertEquals(AppointmentStatus.PENDENTE, appointment.getStatus());
    }

    @Test
    void devePermitirHorarioConflitanteParaProfissionaisDiferentes() {
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(10, 0));

        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId2, serviceId1, availabilityId1,
            futureDate, LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertNotNull(appointment);
    }

    // ==================== criarAgendamentoDeReserva ====================

    @Test
    void deveCriarAgendamentoDeReservaComSucesso() {
        // Cria uma reserva
        Reservation reservation = new Reservation(
            ReservationId.generate(), customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0),
            LocalDateTime.of(futureDate, LocalTime.of(23, 59)));
        reservationRepository.save(reservation);

        // Cria agendamento a partir da reserva
        Appointment appointment = appointmentApplicationService.criarAgendamentoDeReserva(reservation.getId());

        assertNotNull(appointment);
        assertEquals(customerId1, appointment.getCustomerId());
        assertEquals(profId1, appointment.getProfessionalId());
        assertEquals(serviceId1, appointment.getServiceId());
        assertEquals(availabilityId1, appointment.getAvailabilityId());
        assertEquals(reservation.getId(), appointment.getReservationId());
        assertEquals(AppointmentStatus.PENDENTE, appointment.getStatus());

        // Verifica que a reserva foi cancelada
        Reservation reservaCancelada = reservationRepository.findById(reservation.getId());
        assertFalse(reservaCancelada.isAtivo());
        assertEquals(ReservationStatus.CANCELADO, reservaCancelada.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoReservationIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamentoDeReserva(null));
    }

    @Test
    void deveLancarExcecaoQuandoReservaNaoEncontrada() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamentoDeReserva(ReservationId.generate()));
    }

    @Test
    void deveLancarExcecaoQuandoReservaNaoEstaAtiva() {
        Reservation reservation = new Reservation(
            ReservationId.generate(), customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0),
            LocalDateTime.of(futureDate, LocalTime.of(23, 59)));
        reservation.cancelar();
        reservationRepository.save(reservation);

        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.criarAgendamentoDeReserva(reservation.getId()));
    }

    // ==================== buscarPorId ====================

    @Test
    void deveBuscarPorId() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        AppointmentId id = appointment.getId();

        Optional<Appointment> encontrado = appointmentApplicationService.buscarPorId(id);

        assertTrue(encontrado.isPresent());
        assertEquals(appointment.getId(), encontrado.get().getId());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        assertFalse(appointmentApplicationService.buscarPorId(AppointmentId.generate()).isPresent());
    }

    @Test
    void deveRetornarVazioQuandoIdNulo() {
        assertFalse(appointmentApplicationService.buscarPorId(null).isPresent());
    }

    // ==================== listarTodos ====================

    @Test
    void deveListarTodos() {
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertEquals(2, appointmentApplicationService.listarTodos().size());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() {
        assertTrue(appointmentApplicationService.listarTodos().isEmpty());
    }

    // ==================== confirmarAgendamento ====================

    @Test
    void deveConfirmarAgendamento() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        Appointment confirmado = appointmentApplicationService.confirmarAgendamento(appointment.getId());

        assertEquals(AppointmentStatus.CONFIRMADO, confirmado.getStatus());
        assertTrue(confirmado.isAtivo());
        assertFalse(confirmado.podeConfirmar());
        assertTrue(confirmado.podeCancelar());
        assertTrue(confirmado.podeConcluir());
    }

    @Test
    void deveLancarExcecaoQuandoConfirmarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.confirmarAgendamento(AppointmentId.generate()));
    }

    @Test
    void deveLancarExcecaoQuandoConfirmarJaConfirmado() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.confirmarAgendamento(appointment.getId());

        assertThrows(IllegalStateException.class, () ->
            appointmentApplicationService.confirmarAgendamento(appointment.getId()));
    }

    // ==================== cancelarAgendamento ====================

    @Test
    void deveCancelarAgendamentoPendente() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        Appointment cancelado = appointmentApplicationService.cancelarAgendamento(appointment.getId());

        assertEquals(AppointmentStatus.CANCELADO, cancelado.getStatus());
        assertFalse(cancelado.isAtivo());
    }

    @Test
    void deveCancelarAgendamentoConfirmado() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.confirmarAgendamento(appointment.getId());

        Appointment cancelado = appointmentApplicationService.cancelarAgendamento(appointment.getId());

        assertEquals(AppointmentStatus.CANCELADO, cancelado.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoCancelarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.cancelarAgendamento(AppointmentId.generate()));
    }

    @Test
    void deveLancarExcecaoQuandoCancelarJaConcluido() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.confirmarAgendamento(appointment.getId());
        appointmentApplicationService.concluirAgendamento(appointment.getId());

        assertThrows(IllegalStateException.class, () ->
            appointmentApplicationService.cancelarAgendamento(appointment.getId()));
    }

    // ==================== concluirAgendamento ====================

    @Test
    void deveConcluirAgendamento() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.confirmarAgendamento(appointment.getId());

        Appointment concluido = appointmentApplicationService.concluirAgendamento(appointment.getId());

        assertEquals(AppointmentStatus.CONCLUIDO, concluido.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoConcluirInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentApplicationService.concluirAgendamento(AppointmentId.generate()));
    }

    @Test
    void deveLancarExcecaoQuandoConcluirPendente() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertThrows(IllegalStateException.class, () ->
            appointmentApplicationService.concluirAgendamento(appointment.getId()));
    }

    // ==================== existe ====================

    @Test
    void deveRetornarTrueQuandoExiste() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertTrue(appointmentApplicationService.existe(appointment.getId()));
    }

    @Test
    void deveRetornarFalseQuandoNaoExiste() {
        assertFalse(appointmentApplicationService.existe(AppointmentId.generate()));
    }

    @Test
    void deveRetornarFalseQuandoIdNulo() {
        assertFalse(appointmentApplicationService.existe(null));
    }

    // ==================== listarAtivos ====================

    @Test
    void deveListarApenasAtivos() {
        Appointment ativo = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Appointment cancelado = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        appointmentApplicationService.cancelarAgendamento(cancelado.getId());

        List<Appointment> ativos = appointmentApplicationService.listarAtivos();
        assertEquals(1, ativos.size());
        assertTrue(ativos.stream().allMatch(Appointment::isAtivo));
    }

    // ==================== DELETE (cancel) ====================

    @Test
    void deleteDeveCancelarAgendamento() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        Appointment cancelado = appointmentApplicationService.cancelarAgendamento(appointment.getId());

        assertEquals(AppointmentStatus.CANCELADO, cancelado.getStatus());
    }

    // ==================== Fluxo completo ====================

    @Test
    void fluxoCompletoPendenteConfirmadoConcluido() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        assertEquals(AppointmentStatus.PENDENTE, appointment.getStatus());

        appointment = appointmentApplicationService.confirmarAgendamento(appointment.getId());
        assertEquals(AppointmentStatus.CONFIRMADO, appointment.getStatus());

        appointment = appointmentApplicationService.concluirAgendamento(appointment.getId());
        assertEquals(AppointmentStatus.CONCLUIDO, appointment.getStatus());
    }

    // ==================== Agendamento cancelado não impede criação de novo ====================

    @Test
    void agendamentoCanceladoNaoImpedeCriacaoDeNovo() {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(10, 0));
        appointmentApplicationService.cancelarAgendamento(appointment.getId());

        Appointment novo = appointmentApplicationService.criarAgendamento(
            customerId2, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertNotNull(novo);
        assertEquals(AppointmentStatus.PENDENTE, novo.getStatus());
    }
}