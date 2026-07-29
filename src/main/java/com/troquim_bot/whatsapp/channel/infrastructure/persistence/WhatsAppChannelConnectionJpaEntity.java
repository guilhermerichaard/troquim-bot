package com.troquim_bot.whatsapp.channel.infrastructure.persistence;

import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Conexão de canal WhatsApp de um tenant. Tabela de INTEGRAÇÃO (ver V7).
 *
 * A credencial é gravada apenas cifrada (base64 do AES-256-GCM), com o IV daquela
 * escrita e a versão da chave. Não existe coluna com token em claro, então nenhum
 * caminho de leitura consegue produzir uma credencial utilizável sem a chave.
 *
 * O status é STRING (não ordinal) para que inserir um estado novo no meio do enum não
 * reinterprete linhas antigas.
 */
@Entity
@Table(name = "whatsapp_channel_connections")
public class WhatsAppChannelConnectionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChannelConnectionStatus status;

    @Column(name = "state_token", length = 120, unique = true)
    private String stateToken;

    @Column(name = "state_expira_em")
    private LocalDateTime stateExpiraEm;

    @Column(name = "waba_id", length = 60)
    private String wabaId;

    @Column(name = "phone_number_id", length = 60)
    private String phoneNumberId;

    @Column(name = "credencial_cifrada", columnDefinition = "TEXT")
    private String credencialCifrada;

    @Column(name = "credencial_iv", length = 64)
    private String credencialIv;

    @Column(name = "key_version")
    private Integer keyVersion;

    @Column(name = "falha_motivo", length = 120)
    private String falhaMotivo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected WhatsAppChannelConnectionJpaEntity() {
        // JPA
    }

    public WhatsAppChannelConnectionJpaEntity(UUID id, UUID businessId) {
        this.id = id;
        this.businessId = businessId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public ChannelConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ChannelConnectionStatus status) {
        this.status = status;
    }

    public String getStateToken() {
        return stateToken;
    }

    public void setStateToken(String stateToken) {
        this.stateToken = stateToken;
    }

    public LocalDateTime getStateExpiraEm() {
        return stateExpiraEm;
    }

    public void setStateExpiraEm(LocalDateTime stateExpiraEm) {
        this.stateExpiraEm = stateExpiraEm;
    }

    public String getWabaId() {
        return wabaId;
    }

    public void setWabaId(String wabaId) {
        this.wabaId = wabaId;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getCredencialCifrada() {
        return credencialCifrada;
    }

    public void setCredencialCifrada(String credencialCifrada) {
        this.credencialCifrada = credencialCifrada;
    }

    public String getCredencialIv() {
        return credencialIv;
    }

    public void setCredencialIv(String credencialIv) {
        this.credencialIv = credencialIv;
    }

    public Integer getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(Integer keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getFalhaMotivo() {
        return falhaMotivo;
    }

    public void setFalhaMotivo(String falhaMotivo) {
        this.falhaMotivo = falhaMotivo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    /** Nunca inclui credencial nem nonce. */
    @Override
    public String toString() {
        return "WhatsAppChannelConnectionJpaEntity[id=" + id
                + ", businessId=" + businessId + ", status=" + status + "]";
    }
}
