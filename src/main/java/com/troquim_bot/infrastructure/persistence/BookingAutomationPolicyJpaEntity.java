package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.automation.BookingAutomationPolicy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="business_booking_automation")
public class BookingAutomationPolicyJpaEntity {
    @Id
    @Column(name="business_id", nullable=false)
    private UUID businessId;
    @Column(name="reminder_enabled", nullable=false)
    private boolean reminderEnabled;
    @Column(name="reminder_hours_before", nullable=false)
    private int reminderHoursBefore;
    @Column(name="cancellation_min_hours", nullable=false)
    private int cancellationMinHours;
    @Column(name="upsell_enabled", nullable=false)
    private boolean upsellEnabled;
    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    protected BookingAutomationPolicyJpaEntity() {}

    public static BookingAutomationPolicyJpaEntity of(UUID businessId, BookingAutomationPolicy policy) {
        BookingAutomationPolicyJpaEntity e=new BookingAutomationPolicyJpaEntity();
        e.businessId=businessId;
        e.reminderEnabled=policy.reminderEnabled();
        e.reminderHoursBefore=policy.reminderHoursBefore();
        e.cancellationMinHours=policy.cancellationMinHours();
        e.upsellEnabled=policy.upsellEnabled();
        e.updatedAt=LocalDateTime.now();
        return e;
    }

    public BookingAutomationPolicy toDomain() {
        return new BookingAutomationPolicy(reminderEnabled, reminderHoursBefore, cancellationMinHours, upsellEnabled);
    }
}
