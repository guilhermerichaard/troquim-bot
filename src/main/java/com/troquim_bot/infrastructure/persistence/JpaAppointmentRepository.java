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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter JPA que implementa AppointmentRepository.
 * 
 * Faz o mapeamento entre o aggregate Appointment (domínio)
 * e a entidade JPA AppointmentJpaEntity (infraestrutura).
 * 
 * Anotado com @Primary para ser usado pelo Spring por padrão,
 * enquanto InMemoryAppointmentRepository pode ser usado em testes.
 */
@Repository
@Primary
public class JpaAppointmentRepository implements AppointmentRepository {

    private final SpringDataAppointmentRepository springDataRepository;

    public JpaAppointmentRepository(SpringDataAppointmentRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Appointment save(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment não pode ser nulo");
        }
        AppointmentJpaEntity entity = toEntity(appointment);
        AppointmentJpaEntity saved = springDataRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Appointment findById(AppointmentId id) {
        if (id == null) {
            return null;
        }
        return springDataRepository.findById(id.getValue())
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public boolean exists(AppointmentId id) {
        if (id == null) {
            return false;
        }
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public List<Appointment> findAll() {
        return springDataRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date) {
        if (professionalId == null || date == null) {
            return List.of();
        }
        // Como o Spring Data não tem query derivada, buscamos todos e filtramos
        return springDataRepository.findAll().stream()
                .filter(e -> e.getProfessionalId().equals(professionalId.getValue())
                        && e.getDate().equals(date))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> findByCustomerId(CustomerId customerId) {
        if (customerId == null) {
            return List.of();
        }
        return springDataRepository.findAll().stream()
                .filter(e -> e.getCustomerId().equals(customerId.getValue()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(AppointmentId id) {
        if (id != null) {
            springDataRepository.deleteById(id.getValue());
        }
    }

    // ==================== MAPEAMENTO ====================

    private AppointmentJpaEntity toEntity(Appointment appointment) {
        return new AppointmentJpaEntity(
                appointment.getId().getValue(),
                appointment.getCustomerId().getValue(),
                appointment.getProfessionalId().getValue(),
                appointment.getServiceId().getValue(),
                appointment.getAvailabilityId().getValue(),
                appointment.getReservationId() != null ? appointment.getReservationId().getValue() : null,
                appointment.getDate(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus().name(),
                appointment.getCriadoEm(),
                appointment.getAtualizadoEm()
        );
    }

    private Appointment toDomain(AppointmentJpaEntity entity) {
        AppointmentId appointmentId = AppointmentId.from(entity.getId());
        CustomerId customerId = CustomerId.from(entity.getCustomerId());
        ProfessionalId professionalId = ProfessionalId.from(entity.getProfessionalId());
        ServiceId serviceId = ServiceId.from(entity.getServiceId());
        AvailabilityId availabilityId = AvailabilityId.from(entity.getAvailabilityId());
        ReservationId reservationId = entity.getReservationId() != null
                ? ReservationId.from(entity.getReservationId())
                : null;
        AppointmentStatus status = AppointmentStatus.valueOf(entity.getStatus());

        return new Appointment(
                appointmentId,
                customerId,
                professionalId,
                serviceId,
                availabilityId,
                reservationId,
                entity.getDate(),
                entity.getStartTime(),
                entity.getEndTime(),
                status,
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}