package com.troquim_bot.owner.infrastructure;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "owner_otp_challenges")
public class OwnerOtpChallengeJpaEntity {
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(name="business_id",nullable=false) private UUID businessId;
    @Column(name="phone_e164",nullable=false,length=15) private String phoneE164;
    @Column(name="phone_hash",nullable=false,length=64) private String phoneHash;
    @Column(name="code_mac",nullable=false,length=64) private String codeMac;
    @Column(name="purpose",nullable=false,length=20) private String purpose;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="attempts_remaining",nullable=false) private int attemptsRemaining;
    @Column(name="consumed_at") private LocalDateTime consumedAt;
    protected OwnerOtpChallengeJpaEntity(){}
    public OwnerOtpChallengeJpaEntity(UUID id,UUID ownerId,UUID businessId,String phoneE164,String phoneHash,String codeMac,String purpose,LocalDateTime createdAt,LocalDateTime expiresAt,int attemptsRemaining,LocalDateTime consumedAt){
        this.id=id;this.ownerId=ownerId;this.businessId=businessId;this.phoneE164=phoneE164;this.phoneHash=phoneHash;this.codeMac=codeMac;this.purpose=purpose;this.createdAt=createdAt;this.expiresAt=expiresAt;this.attemptsRemaining=attemptsRemaining;this.consumedAt=consumedAt;
    }
    public UUID getId(){return id;} public UUID getOwnerId(){return ownerId;} public UUID getBusinessId(){return businessId;}
    public String getPhoneE164(){return phoneE164;} public String getPhoneHash(){return phoneHash;} public String getCodeMac(){return codeMac;} public String getPurpose(){return purpose;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getExpiresAt(){return expiresAt;} public int getAttemptsRemaining(){return attemptsRemaining;} public LocalDateTime getConsumedAt(){return consumedAt;}
}
