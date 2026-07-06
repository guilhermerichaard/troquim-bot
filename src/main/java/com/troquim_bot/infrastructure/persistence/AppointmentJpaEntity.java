package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entidade JPA para persistência de Appointment.
 * Mapeia a tabela "appointments" no banco H2.
 */
@Entity
@Table(name = "appointments")
public class AppointmentJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "customer_id", nullable = false, columnDefinition = "UUID")
    private UUID customerId;

    @Column(name = "professional_id", nullable = false, columnDefinition = "UUID")
    private UUID professionalId;

    @Column(name = "service_id", nullable = false, columnDefinition = "UUID")
    private UUID serviceId;

    @Column(name = "availability_id", nullable = false, columnDefinition = "UUID")
    private UUID availabilityId;

    @Column(name = "reservation_id", columnDefinition = "UUID")
    private UUID reservationId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /**
     * Construtor padrão exigido pelo JPA.
     */
    protected AppointmentJpaEntity() {}

    public AppointmentJpaEntity(UUID id, UUID customerId, UUID professionalId,
                                UUID serviceId, UUID availabilityId, UUID reservationId,
                                LocalDate date, LocalTime startTime, LocalTime endTime,
                                String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.reservationId = reservationId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProfessionalId() { return professionalId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getAvailabilityId() { return availabilityId; }
    public UUID getReservationId() { return reservationId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}