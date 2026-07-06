package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.service.ServiceId;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter JPA que implementa ReservationRepository.
 * 
 * Faz o mapeamento entre o aggregate Reservation (domínio)
 * e a entidade JPA ReservationJpaEntity (infraestrutura).
 * 
 * Anotado com @Primary para ser usado pelo Spring por padrão,
 * enquanto InMemoryReservationRepository pode ser usado em testes.
 */
@Repository
@Primary
public class JpaReservationRepository implements ReservationRepository {

    private final SpringDataReservationRepository springDataRepository;

    public JpaReservationRepository(SpringDataReservationRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation não pode ser nulo");
        }
        ReservationJpaEntity entity = toEntity(reservation);
        ReservationJpaEntity saved = springDataRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Reservation findById(ReservationId id) {
        if (id == null) {
            return null;
        }
        return springDataRepository.findById(id.getValue())
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public boolean exists(ReservationId id) {
        if (id == null) {
            return false;
        }
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public List<Reservation> findAll() {
        return springDataRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date) {
        if (professionalId == null || date == null) {
            return List.of();
        }
        return springDataRepository.findByProfessionalIdAndDate(professionalId.getValue(), date)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(ReservationId id) {
        if (id != null) {
            springDataRepository.deleteById(id.getValue());
        }
    }

    // ==================== MAPEAMENTO ====================

    private ReservationJpaEntity toEntity(Reservation reservation) {
        return new ReservationJpaEntity(
                reservation.getId().getValue(),
                reservation.getCustomerId().getValue(),
                reservation.getProfessionalId().getValue(),
                reservation.getServiceId().getValue(),
                reservation.getAvailabilityId().getValue(),
                reservation.getDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getExpiresAt(),
                reservation.getStatus().name(),
                reservation.getCriadoEm(),
                reservation.getAtualizadoEm()
        );
    }

    private Reservation toDomain(ReservationJpaEntity entity) {
        ReservationId id = ReservationId.from(entity.getId());
        CustomerId customerId = CustomerId.from(entity.getCustomerId());
        ProfessionalId professionalId = ProfessionalId.from(entity.getProfessionalId());
        ServiceId serviceId = ServiceId.from(entity.getServiceId());
        AvailabilityId availabilityId = AvailabilityId.from(entity.getAvailabilityId());
        ReservationStatus status = ReservationStatus.valueOf(entity.getStatus());

        return new Reservation(
                id,
                customerId,
                professionalId,
                serviceId,
                availabilityId,
                entity.getDate(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getExpiresAt(),
                status,
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}