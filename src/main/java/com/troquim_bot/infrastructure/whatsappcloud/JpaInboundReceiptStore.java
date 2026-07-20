package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.InboundReceiptStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA da idempotência durável. {@code claimPending} usa
 * {@code saveAndFlush} para forçar a checagem da UNIQUE(provider, external_message_id)
 * imediatamente, fazendo a entrega concorrente perdedora receber violação de integridade
 * dentro do limite transacional do {@code InboundReceiptProcessor}.
 */
@Component
@ConditionalOnWhatsAppCloud
public class JpaInboundReceiptStore implements InboundReceiptStore {

    private final SpringDataInboundMessageReceiptRepository repository;

    public JpaInboundReceiptStore(SpringDataInboundMessageReceiptRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StoredReceipt> find(String provider, String externalMessageId) {
        return repository.findByProviderAndExternalMessageId(provider, externalMessageId)
                .map(e -> new StoredReceipt(e.getStatus(), e.getResponseText()));
    }

    @Override
    public void claimPending(String provider, String externalMessageId) {
        LocalDateTime now = LocalDateTime.now();
        InboundMessageReceiptJpaEntity receipt = new InboundMessageReceiptJpaEntity(
                UUID.randomUUID(), provider, externalMessageId, STATUS_PENDING, now, now);
        // Flush imediato: surfacea a violação de UNIQUE agora (serializa concorrência).
        repository.saveAndFlush(receipt);
    }

    @Override
    public void completeProcessing(String provider, String externalMessageId, String responseText) {
        repository.findByProviderAndExternalMessageId(provider, externalMessageId)
                .ifPresent(receipt -> {
                    receipt.setResponseText(responseText);
                    receipt.setAtualizadoEm(LocalDateTime.now());
                    repository.save(receipt);
                });
    }

    @Override
    public void markSent(String provider, String externalMessageId, String outboundMessageId) {
        repository.findByProviderAndExternalMessageId(provider, externalMessageId)
                .ifPresent(receipt -> {
                    receipt.setStatus(STATUS_SENT);
                    receipt.setOutboundMessageId(outboundMessageId);
                    receipt.setAtualizadoEm(LocalDateTime.now());
                    repository.save(receipt);
                });
    }
}
