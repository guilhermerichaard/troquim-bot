package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="appointment_reminder_receipts")
@IdClass(AppointmentReminderReceiptJpaEntity.Key.class)
public class AppointmentReminderReceiptJpaEntity {
    @Id @Column(name="appointment_id",nullable=false)
    private UUID appointmentId;
    @Id @Column(name="reminder_kind",nullable=false,length=30)
    private String reminderKind;
    @Column(name="business_id",nullable=false)
    private UUID businessId;
    @Column(name="sent_at",nullable=false)
    private LocalDateTime sentAt;

    protected AppointmentReminderReceiptJpaEntity() {}

    public AppointmentReminderReceiptJpaEntity(UUID appointmentId, UUID businessId, String reminderKind) {
        this.appointmentId=appointmentId;
        this.businessId=businessId;
        this.reminderKind=reminderKind;
        this.sentAt=LocalDateTime.now();
    }

    public static class Key implements Serializable {
        public UUID appointmentId;
        public String reminderKind;
        public Key() {}
        public Key(UUID appointmentId,String reminderKind){this.appointmentId=appointmentId;this.reminderKind=reminderKind;}
        @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Key k))return false;return Objects.equals(appointmentId,k.appointmentId)&&Objects.equals(reminderKind,k.reminderKind);}
        @Override public int hashCode(){return Objects.hash(appointmentId,reminderKind);}
    }
}
