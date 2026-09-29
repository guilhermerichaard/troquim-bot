package com.troquim_bot.owner.infrastructure;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "owner_phone_credentials")
public class OwnerPhoneCredentialJpaEntity {
    @Id @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "phone_e164", nullable = false, unique = true, length = 15) private String phoneE164;
    @Column(name = "verified_at", nullable = false) private LocalDateTime verifiedAt;
    protected OwnerPhoneCredentialJpaEntity() {}
    public OwnerPhoneCredentialJpaEntity(UUID ownerId, UUID businessId, String phoneE164, LocalDateTime verifiedAt) {
        this.ownerId = ownerId; this.businessId = businessId; this.phoneE164 = phoneE164; this.verifiedAt = verifiedAt;
    }
    public UUID getOwnerId(){return ownerId;} public UUID getBusinessId(){return businessId;}
    public String getPhoneE164(){return phoneE164;} public LocalDateTime getVerifiedAt(){return verifiedAt;}
}
