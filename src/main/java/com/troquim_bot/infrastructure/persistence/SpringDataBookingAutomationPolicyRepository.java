package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataBookingAutomationPolicyRepository
        extends JpaRepository<BookingAutomationPolicyJpaEntity, UUID> {
}
