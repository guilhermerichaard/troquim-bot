package com.troquim_bot.infrastructure.whatsappcloud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataInboundMessageReceiptRepository
        extends JpaRepository<InboundMessageReceiptJpaEntity, UUID> {

    boolean existsByProviderAndExternalMessageId(String provider, String externalMessageId);

    Optional<InboundMessageReceiptJpaEntity> findByProviderAndExternalMessageId(
            String provider, String externalMessageId);
}
