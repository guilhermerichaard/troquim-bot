package com.troquim_bot.infrastructure.whatsappcloud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro durável de processamento de evento externo (idempotência). Tabela de
 * INTEGRAÇÃO, não entidade de negócio. A UNIQUE(provider, external_message_id)
 * garante processamento único e serializa entregas concorrentes.
 */
@Entity
@Table(name = "inbound_message_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inbound_receipt_provider_external_id",
                columnNames = {"provider", "external_message_id"}))
public class InboundMessageReceiptJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "external_message_id", nullable = false, length = 255)
    private String externalMessageId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "outbound_message_id", length = 255)
    private String outboundMessageId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected InboundMessageReceiptJpaEntity() {
    }

    public InboundMessageReceiptJpaEntity(UUID id, String provider, String externalMessageId,
                                          String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.provider = provider;
        this.externalMessageId = externalMessageId;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public String getStatus() {
        return status;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getOutboundMessageId() {
        return outboundMessageId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public void setOutboundMessageId(String outboundMessageId) {
        this.outboundMessageId = outboundMessageId;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
