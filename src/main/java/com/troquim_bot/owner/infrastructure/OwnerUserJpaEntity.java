package com.troquim_bot.owner.infrastructure;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "owner_users")
public class OwnerUserJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected OwnerUserJpaEntity() {}

    public OwnerUserJpaEntity(UUID id, UUID businessId, String email, String senhaHash,
                              String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.businessId = businessId;
        this.email = email;
        this.senhaHash = senhaHash;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public String getStatus() { return status; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
