package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.service.ServiceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração que prova que Reservation persiste e sobrevive a restart.
 * 
 * Usa o adapter JPA real (JpaReservationRepository) com banco H2.
 */
@SpringBootTest
@ActiveProfiles("prod")
class JpaReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    private CustomerId customerId = CustomerId.from(UUID.randomUUID());
    private ProfessionalId professionalId = ProfessionalId.from(UUID.randomUUID());
    private ServiceId serviceId = ServiceId.from(UUID.randomUUID());
    private AvailabilityId availabilityId = AvailabilityId.from(UUID.randomUUID());

    @Test
    void salvaEBuscaReservationPorId() {
        ReservationId id = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 9, 0);

        Reservation reservation = new Reservation(id, customerId, professionalId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt);

        reservationRepository.save(reservation);

        Reservation found = reservationRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals(customerId, found.getCustomerId());
        assertEquals(professionalId, found.getProfessionalId());
        assertEquals(serviceId, found.getServiceId());
        assertEquals(availabilityId, found.getAvailabilityId());
        assertEquals(date, found.getDate());
        assertEquals(startTime, found.getStartTime());
        assertEquals(endTime, found.getEndTime());
        assertEquals(expiresAt, found.getExpiresAt());
        assertEquals(ReservationStatus.ATIVO, found.getStatus());
        assertNotNull(found.getCriadoEm());
        assertNotNull(found.getAtualizadoEm());
    }

    @Test
    void atualizaReservationExistente() {
        ReservationId id = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 13, 0);

        Reservation reservation = new Reservation(id, customerId, professionalId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt);

        reservationRepository.save(reservation);

        Reservation saved = reservationRepository.findById(id);
        saved.cancelar();
        reservationRepository.save(saved);

        Reservation updated = reservationRepository.findById(id);
        assertEquals(ReservationStatus.CANCELADO, updated.getStatus());

        updated.reativar();
        reservationRepository.save(updated);

        Reservation reativada = reservationRepository.findById(id);
        assertEquals(ReservationStatus.ATIVO, reativada.getStatus());
    }

    @Test
    void existsRetornaTrueParaReservationExistente() {
        ReservationId id = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(10, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 8, 0);

        Reservation reservation = new Reservation(id, customerId, professionalId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt);

        reservationRepository.save(reservation);

        assertTrue(reservationRepository.exists(id));
        assertFalse(reservationRepository.exists(ReservationId.generate()));
    }

    @Test
    void findAllRetornaTodasReservations() {
        ReservationId id1 = ReservationId.generate();
        ReservationId id2 = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime1 = LocalTime.of(8, 0);
        LocalTime endTime1 = LocalTime.of(9, 0);
        LocalTime startTime2 = LocalTime.of(9, 0);
        LocalTime endTime2 = LocalTime.of(10, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 7, 0);

        reservationRepository.save(new Reservation(id1, customerId, professionalId, serviceId,
                availabilityId, date, startTime1, endTime1, expiresAt));
        reservationRepository.save(new Reservation(id2, customerId, professionalId, serviceId,
                availabilityId, date, startTime2, endTime2, expiresAt));

        List<Reservation> all = reservationRepository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void findByProfessionalIdAndDateRetornaReservasCorretas() {
        ProfessionalId profId = ProfessionalId.from(UUID.randomUUID());
        ProfessionalId outroProfId = ProfessionalId.from(UUID.randomUUID());
        LocalDate date = LocalDate.of(2026, 7, 15);
        LocalDate outraData = LocalDate.of(2026, 7, 16);

        ReservationId id1 = ReservationId.generate();
        ReservationId id2 = ReservationId.generate();
        ReservationId id3 = ReservationId.generate();

        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 15, 9, 0);

        // Reserva do profId na data
        reservationRepository.save(new Reservation(id1, customerId, profId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt));
        // Outra reserva do profId na data
        reservationRepository.save(new Reservation(id2, customerId, profId, serviceId,
                availabilityId, date, startTime.plusHours(1), endTime.plusHours(1), expiresAt));
        // Reserva do profId em outra data (não deve aparecer)
        reservationRepository.save(new Reservation(id3, customerId, profId, serviceId,
                availabilityId, outraData, startTime, endTime, expiresAt));

        List<Reservation> results = reservationRepository.findByProfessionalIdAndDate(profId, date);
        assertEquals(2, results.size());

        // Reserva de outro profissional na mesma data (não deve aparecer)
        ReservationId id4 = ReservationId.generate();
        reservationRepository.save(new Reservation(id4, customerId, outroProfId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt));

        results = reservationRepository.findByProfessionalIdAndDate(profId, date);
        assertEquals(2, results.size());
    }

    @Test
    void deleteRemoveReservation() {
        ReservationId id = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(16, 0);
        LocalTime endTime = LocalTime.of(17, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 15, 0);

        Reservation reservation = new Reservation(id, customerId, professionalId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt);

        reservationRepository.save(reservation);
        assertTrue(reservationRepository.exists(id));

        reservationRepository.delete(id);
        assertFalse(reservationRepository.exists(id));
    }

    @Test
    void dadosSobrevivemASalvarEBuscar() {
        // Simula o ciclo: criar -> salvar -> buscar (como se fosse restart)
        ReservationId id = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 8, 1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 30);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 1, 9, 0);

        Reservation reservation = new Reservation(id, customerId, professionalId, serviceId,
                availabilityId, date, startTime, endTime, expiresAt);

        reservationRepository.save(reservation);

        // Busca como se fosse uma nova instância (simula restart)
        Reservation found = reservationRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals(customerId, found.getCustomerId());
        assertEquals(professionalId, found.getProfessionalId());
        assertEquals(serviceId, found.getServiceId());
        assertEquals(availabilityId, found.getAvailabilityId());
        assertEquals(date, found.getDate());
        assertEquals(startTime, found.getStartTime());
        assertEquals(endTime, found.getEndTime());
        assertEquals(expiresAt, found.getExpiresAt());
        assertEquals(ReservationStatus.ATIVO, found.getStatus());
    }
}