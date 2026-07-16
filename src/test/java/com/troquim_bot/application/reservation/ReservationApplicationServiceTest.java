package com.troquim_bot.application.reservation;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
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

class ReservationApplicationServiceTest {

    private ReservationApplicationService reservationApplicationService;
    private InMemoryReservationRepository reservationRepository;

    private final CustomerId customerId1 = CustomerId.from(UUID.randomUUID());
    private final CustomerId customerId2 = CustomerId.from(UUID.randomUUID());
    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ProfessionalId profId2 = ProfessionalId.from(UUID.randomUUID());
    private final ServiceId serviceId1 = ServiceId.from(UUID.randomUUID());
    private final AvailabilityId availabilityId1 = AvailabilityId.from(UUID.randomUUID());

    private final LocalDate data = LocalDate.now().plusDays(30);
    private final LocalDateTime expiresAt = LocalDateTime.of(data, LocalTime.of(23, 59));

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        reservationApplicationService = new ReservationApplicationService(reservationRepository);
    }

    // ==================== criarReserva ====================

    @Test
    void deveCriarReservaComSucesso() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);

        assertNotNull(reservation);
        assertNotNull(reservation.getId());
        assertEquals(customerId1, reservation.getCustomerId());
        assertEquals(profId1, reservation.getProfessionalId());
        assertEquals(serviceId1, reservation.getServiceId());
        assertEquals(availabilityId1, reservation.getAvailabilityId());
        assertEquals(data, reservation.getDate());
        assertEquals(LocalTime.of(8, 0), reservation.getStartTime());
        assertEquals(LocalTime.of(9, 0), reservation.getEndTime());
        assertEquals(expiresAt, reservation.getExpiresAt());
        assertEquals(ReservationStatus.ATIVO, reservation.getStatus());
        assertTrue(reservation.isAtivo());
        assertFalse(reservation.isExpirada());
        assertNotNull(reservation.getCriadoEm());
        assertNotNull(reservation.getAtualizadoEm());
    }

    @Test
    void deveLancarExcecaoQuandoCustomerIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                null, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoProfessionalIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, null, serviceId1, availabilityId1,
                data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoServiceIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, null, availabilityId1,
                data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoAvailabilityIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, null,
                data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoDataNula() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                null, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                data, null, LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoEndTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(8, 0), null, expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoExpiresAtNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(8, 0), LocalTime.of(9, 0), null));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeMaiorQueEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(10, 0), LocalTime.of(9, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeIgualEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId1, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(8, 0), LocalTime.of(8, 0), expiresAt));
    }

    @Test
    void deveLancarExcecaoQuandoHorarioConflitante() {
        // Cria primeira reserva
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        // Tenta criar reserva no mesmo horário
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.criarReserva(
                customerId2, profId1, serviceId1, availabilityId1,
                data, LocalTime.of(9, 0), LocalTime.of(11, 0), expiresAt));
    }

    @Test
    void devePermitirHorarioNaoConflitante() {
        // Cria primeira reserva
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        // Cria reserva em horário não conflitante
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(10, 0), LocalTime.of(12, 0), expiresAt);

        assertNotNull(reservation);
        assertEquals(ReservationStatus.ATIVO, reservation.getStatus());
    }

    @Test
    void devePermitirHorarioConflitanteParaProfissionaisDiferentes() {
        // Cria reserva para profissional 1
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        // Cria reserva no mesmo horário para profissional 2
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId2, serviceId1, availabilityId1,
            data, LocalTime.of(9, 0), LocalTime.of(11, 0), expiresAt);

        assertNotNull(reservation);
    }

    @Test
    void devePermitirHorarioConflitanteEmDatasDiferentes() {
        // Cria reserva em uma data
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        // Cria reserva no mesmo horário em outra data
        LocalDate outraData = LocalDate.of(2026, 7, 11);
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            outraData, LocalTime.of(9, 0), LocalTime.of(11, 0), expiresAt);

        assertNotNull(reservation);
    }

    // ==================== buscarPorId ====================

    @Test
    void deveBuscarReservaPorId() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        ReservationId id = reservation.getId();

        Optional<Reservation> encontrado = reservationApplicationService.buscarPorId(id);

        assertTrue(encontrado.isPresent());
        assertEquals(reservation.getId(), encontrado.get().getId());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        Optional<Reservation> encontrado = reservationApplicationService.buscarPorId(ReservationId.generate());

        assertFalse(encontrado.isPresent());
    }

    @Test
    void deveRetornarVazioQuandoIdNulo() {
        Optional<Reservation> encontrado = reservationApplicationService.buscarPorId(null);

        assertFalse(encontrado.isPresent());
    }

    // ==================== listarTodos ====================

    @Test
    void deveListarTodos() {
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(10, 0), LocalTime.of(11, 0), expiresAt);

        List<Reservation> reservations = reservationApplicationService.listarTodos();

        assertEquals(2, reservations.size());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() {
        List<Reservation> reservations = reservationApplicationService.listarTodos();

        assertTrue(reservations.isEmpty());
    }

    // ==================== listarAtivos ====================

    @Test
    void deveListarApenasAtivos() {
        Reservation ativo1 = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        Reservation ativo2 = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(10, 0), LocalTime.of(11, 0), expiresAt);
        Reservation cancelado = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(14, 0), LocalTime.of(15, 0), expiresAt);

        reservationApplicationService.cancelarReserva(cancelado.getId());

        List<Reservation> ativos = reservationApplicationService.listarAtivos();

        assertEquals(2, ativos.size());
        assertTrue(ativos.stream().allMatch(Reservation::isAtivo));
    }

    // ==================== atualizarData ====================

    @Test
    void deveAtualizarData() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);

        LocalDate novaData = LocalDate.of(2026, 7, 11);
        Reservation atualizado = reservationApplicationService.atualizarData(reservation.getId(), novaData);

        assertEquals(novaData, atualizado.getDate());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarDataDeInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.atualizarData(ReservationId.generate(), data));
    }

    // ==================== atualizarStartTime ====================

    @Test
    void deveAtualizarStartTime() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        Reservation atualizado = reservationApplicationService.atualizarStartTime(reservation.getId(), LocalTime.of(9, 0));

        assertEquals(LocalTime.of(9, 0), atualizado.getStartTime());
    }

    // ==================== atualizarEndTime ====================

    @Test
    void deveAtualizarEndTime() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);

        Reservation atualizado = reservationApplicationService.atualizarEndTime(reservation.getId(), LocalTime.of(12, 0));

        assertEquals(LocalTime.of(12, 0), atualizado.getEndTime());
    }

    // ==================== atualizarExpiresAt ====================

    @Test
    void deveAtualizarExpiresAt() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);

        LocalDateTime novoExpiresAt = LocalDateTime.of(2026, 7, 11, 23, 59);
        Reservation atualizado = reservationApplicationService.atualizarExpiresAt(reservation.getId(), novoExpiresAt);

        assertEquals(novoExpiresAt, atualizado.getExpiresAt());
    }

    // ==================== cancelarReserva ====================

    @Test
    void deveCancelarReserva() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);

        Reservation cancelado = reservationApplicationService.cancelarReserva(reservation.getId());

        assertEquals(ReservationStatus.CANCELADO, cancelado.getStatus());
        assertFalse(cancelado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoCancelarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.cancelarReserva(ReservationId.generate()));
    }

    // ==================== reativarReserva ====================

    @Test
    void deveReativarReserva() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        reservationApplicationService.cancelarReserva(reservation.getId());

        Reservation reativado = reservationApplicationService.reativarReserva(reservation.getId());

        assertEquals(ReservationStatus.ATIVO, reativado.getStatus());
        assertTrue(reativado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoReativarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            reservationApplicationService.reativarReserva(ReservationId.generate()));
    }

    // ==================== existe ====================

    @Test
    void deveRetornarTrueQuandoExiste() {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);

        assertTrue(reservationApplicationService.existe(reservation.getId()));
    }

    @Test
    void deveRetornarFalseQuandoNaoExiste() {
        assertFalse(reservationApplicationService.existe(ReservationId.generate()));
    }

    @Test
    void deveRetornarFalseQuandoIdNulo() {
        assertFalse(reservationApplicationService.existe(null));
    }

    // ==================== Reserva cancelada não conflita ====================

    @Test
    void reservaCanceladaNaoImpedeCriacaoDeNova() {
        // Cria e cancela uma reserva
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(10, 0), expiresAt);
        reservationApplicationService.cancelarReserva(reservation.getId());

        // Deve ser possível criar outra no mesmo horário (a anterior está cancelada)
        Reservation nova = reservationApplicationService.criarReserva(
            customerId2, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(9, 0), LocalTime.of(11, 0), expiresAt);

        assertNotNull(nova);
        assertEquals(ReservationStatus.ATIVO, nova.getStatus());
    }
}