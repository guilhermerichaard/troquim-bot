package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAppointmentReminderReceiptRepository
        extends JpaRepository<AppointmentReminderReceiptJpaEntity, AppointmentReminderReceiptJpaEntity.Key> {
}
