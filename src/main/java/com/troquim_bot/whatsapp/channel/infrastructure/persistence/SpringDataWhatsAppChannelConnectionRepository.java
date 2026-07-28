package com.troquim_bot.whatsapp.channel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositório Spring Data das conexões de canal WhatsApp. */
public interface SpringDataWhatsAppChannelConnectionRepository
        extends JpaRepository<WhatsAppChannelConnectionJpaEntity, UUID> {

    Optional<WhatsAppChannelConnectionJpaEntity> findByBusinessId(UUID businessId);

    Optional<WhatsAppChannelConnectionJpaEntity> findByStateToken(String stateToken);
}
