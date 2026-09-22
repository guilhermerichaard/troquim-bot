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
 * Mapeamento mínimo da booking_waitlist.
 *
 * O adapter continua usando SQL explícito em {@link JpaWaitlistRepository}. Esta entidade
 * existe também para manter paridade de schema nos profiles de teste que usam
 * ddl-auto=create-drop, exatamente como a entidade da migration V15.
 */
@Entity
@Table(name = "booking_waitlist")
public class BookingWaitlistJpaEntity {

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "earliest_time")
    private LocalTime earliestTime;

    @Column(name = "latest_time")
    private LocalTime latestTime;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    protected BookingWaitlistJpaEntity() {
    }
}
