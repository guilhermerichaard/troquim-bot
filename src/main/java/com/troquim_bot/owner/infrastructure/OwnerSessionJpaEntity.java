package com.troquim_bot.owner.infrastructure;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "owner_sessions")
public class OwnerSessionJpaEntity {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    protected OwnerSessionJpaEntity() {}

    public OwnerSessionJpaEntity(String tokenHash, UUID ownerId, UUID businessId,
                                 LocalDateTime criadaEm, LocalDateTime expiraEm) {
        this.tokenHash = tokenHash;
        this.ownerId = ownerId;
        this.businessId = businessId;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getBusinessId() { return businessId; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
}
