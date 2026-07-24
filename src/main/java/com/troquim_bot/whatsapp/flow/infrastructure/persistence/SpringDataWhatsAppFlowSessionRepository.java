package com.troquim_bot.whatsapp.flow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositório Spring Data das sessões de Flow. */
public interface SpringDataWhatsAppFlowSessionRepository
        extends JpaRepository<WhatsAppFlowSessionJpaEntity, String> {
}
