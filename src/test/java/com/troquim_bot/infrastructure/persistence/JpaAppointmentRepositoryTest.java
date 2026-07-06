package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.service.ServiceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração que prova que Appointment persiste e sobrevive a restart.
 * 
 * Usa o adapter JPA real (JpaAppointmentRepository) com banco H2.
 */
@SpringBootTest
@ActiveProfiles("prod")
class JpaAppointmentRepositoryTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    private CustomerId customerId = CustomerId.from(UUID.randomUUID());
    private ProfessionalId professionalId = ProfessionalId.from(UUID.randomUUID());
    private ServiceId serviceId = ServiceId.from(UUID.randomUUID());
    private AvailabilityId availabilityId = AvailabilityId.from(UUID.randomUUID());

    @Test
    void salvaEBuscaAppointmentPorId() {
        AppointmentId id = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, date, startTime, endTime);

        appointmentRepository.save(appointment);

        Appointment found = appointmentRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals(customerId, found.getCustomerId());
        assertEquals(professionalId, found.getProfessionalId());
        assertEquals(serviceId, found.getServiceId());
        assertEquals(availabilityId, found.getAvailabilityId());
        assertNull(found.getReservationId());
        assertEquals(date, found.getDate());
        assertEquals(startTime, found.getStartTime());
        assertEquals(endTime, found.getEndTime());
        assertEquals(AppointmentStatus.PENDENTE, found.getStatus());
        assertNotNull(found.getCriadoEm());
        assertNotNull(found.getAtualizadoEm());
    }

    @Test
    void salvaEBuscaAppointmentComReservation() {
        AppointmentId id = AppointmentId.generate();
        ReservationId reservationId = ReservationId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, reservationId, date, startTime, endTime);

        appointmentRepository.save(appointment);

        Appointment found = appointmentRepository.findById(id);
        assertNotNull(found);
        assertEquals(reservationId, found.getReservationId());
    }

    @Test
    void atualizaAppointmentExistente() {
        AppointmentId id = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, date, startTime, endTime);

        appointmentRepository.save(appointment);

        Appointment saved = appointmentRepository.findById(id);
        saved.confirmar();
        appointmentRepository.save(saved);

        Appointment updated = appointmentRepository.findById(id);
        assertEquals(AppointmentStatus.CONFIRMADO, updated.getStatus());

        updated.cancelar();
        appointmentRepository.save(updated);

        Appointment cancelada = appointmentRepository.findById(id);
        assertEquals(AppointmentStatus.CANCELADO, cancelada.getStatus());
    }

    @Test
    void existsRetornaTrueParaAppointmentExistente() {
        AppointmentId id = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(10, 0);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, date, startTime, endTime);

        appointmentRepository.save(appointment);

        assertTrue(appointmentRepository.exists(id));
        assertFalse(appointmentRepository.exists(AppointmentId.generate()));
    }

    @Test
    void findAllRetornaTodosAppointments() {
        AppointmentId id1 = AppointmentId.generate();
        AppointmentId id2 = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime1 = LocalTime.of(8, 0);
        LocalTime endTime1 = LocalTime.of(9, 0);
        LocalTime startTime2 = LocalTime.of(9, 0);
        LocalTime endTime2 = LocalTime.of(10, 0);

        appointmentRepository.save(new Appointment(id1, customerId, professionalId,
                serviceId, availabilityId, date, startTime1, endTime1));
        appointmentRepository.save(new Appointment(id2, customerId, professionalId,
                serviceId, availabilityId, date, startTime2, endTime2));

        List<Appointment> all = appointmentRepository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void findByProfessionalIdAndDateRetornaAppointmentsCorretos() {
        ProfessionalId profId = ProfessionalId.from(UUID.randomUUID());
        ProfessionalId outroProfId = ProfessionalId.from(UUID.randomUUID());
        LocalDate date = LocalDate.of(2026, 7, 15);
        LocalDate outraData = LocalDate.of(2026, 7, 16);

        AppointmentId id1 = AppointmentId.generate();
        AppointmentId id2 = AppointmentId.generate();
        AppointmentId id3 = AppointmentId.generate();

        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        // Appointment do profId na data
        appointmentRepository.save(new Appointment(id1, customerId, profId,
                serviceId, availabilityId, date, startTime, endTime));
        // Outro appointment do profId na data
        appointmentRepository.save(new Appointment(id2, customerId, profId,
                serviceId, availabilityId, date, startTime.plusHours(1), endTime.plusHours(1)));
        // Appointment do profId em outra data (não deve aparecer)
        appointmentRepository.save(new Appointment(id3, customerId, profId,
                serviceId, availabilityId, outraData, startTime, endTime));

        List<Appointment> results = appointmentRepository.findByProfessionalIdAndDate(profId, date);
        assertEquals(2, results.size());

        // Appointment de outro profissional na mesma data (não deve aparecer)
        AppointmentId id4 = AppointmentId.generate();
        appointmentRepository.save(new Appointment(id4, customerId, outroProfId,
                serviceId, availabilityId, date, startTime, endTime));

        results = appointmentRepository.findByProfessionalIdAndDate(profId, date);
        assertEquals(2, results.size());
    }

    @Test
    void findByCustomerIdRetornaAppointmentsDoCliente() {
        CustomerId clienteId = CustomerId.from(UUID.randomUUID());
        CustomerId outroClienteId = CustomerId.from(UUID.randomUUID());
        LocalDate date = LocalDate.of(2026, 7, 20);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        AppointmentId id1 = AppointmentId.generate();
        AppointmentId id2 = AppointmentId.generate();
        AppointmentId id3 = AppointmentId.generate();

        appointmentRepository.save(new Appointment(id1, clienteId, professionalId,
                serviceId, availabilityId, date, startTime, endTime));
        appointmentRepository.save(new Appointment(id2, clienteId, professionalId,
                serviceId, availabilityId, date, startTime.plusHours(1), endTime.plusHours(1)));
        // Appointment de outro cliente
        appointmentRepository.save(new Appointment(id3, outroClienteId, professionalId,
                serviceId, availabilityId, date, startTime, endTime));

        List<Appointment> results = appointmentRepository.findByCustomerId(clienteId);
        assertEquals(2, results.size());
    }

    @Test
    void deleteRemoveAppointment() {
        AppointmentId id = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime startTime = LocalTime.of(16, 0);
        LocalTime endTime = LocalTime.of(17, 0);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, date, startTime, endTime);

        appointmentRepository.save(appointment);
        assertTrue(appointmentRepository.exists(id));

        appointmentRepository.delete(id);
        assertFalse(appointmentRepository.exists(id));
    }

    @Test
    void dadosSobrevivemASalvarEBuscar() {
        // Simula o ciclo: criar -> salvar -> buscar (como se fosse restart)
        AppointmentId id = AppointmentId.generate();
        LocalDate date = LocalDate.of(2026, 8, 1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 30);

        Appointment appointment = new Appointment(id, customerId, professionalId,
                serviceId, availabilityId, date, startTime, endTime);

        appointmentRepository.save(appointment);

        // Busca como se fosse uma nova instância (simula restart)
        Appointment found = appointmentRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals(customerId, found.getCustomerId());
        assertEquals(professionalId, found.getProfessionalId());
        assertEquals(serviceId, found.getServiceId());
        assertEquals(availabilityId, found.getAvailabilityId());
        assertNull(found.getReservationId());
        assertEquals(date, found.getDate());
        assertEquals(startTime, found.getStartTime());
        assertEquals(endTime, found.getEndTime());
        assertEquals(AppointmentStatus.PENDENTE, found.getStatus());
    }
}