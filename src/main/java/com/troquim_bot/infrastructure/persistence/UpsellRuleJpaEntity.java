package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.automation.UpsellRuleRepository;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="service_upsell_rules")
@IdClass(UpsellRuleJpaEntity.Key.class)
public class UpsellRuleJpaEntity {
    @Id @Column(name="business_id",nullable=false)
    private UUID businessId;
    @Id @Column(name="base_service_id",nullable=false)
    private UUID baseServiceId;
    @Column(name="addon_service_id",nullable=false)
    private UUID addonServiceId;
    @Column(name="active",nullable=false)
    private boolean active;
    @Column(name="updated_at",nullable=false)
    private LocalDateTime updatedAt;

    protected UpsellRuleJpaEntity(){}

    public static UpsellRuleJpaEntity from(UpsellRuleRepository.Rule rule){
        UpsellRuleJpaEntity e=new UpsellRuleJpaEntity();
        e.businessId=rule.businessId().getValue();
        e.baseServiceId=rule.baseServiceId().getValue();
        e.addonServiceId=rule.addonServiceId().getValue();
        e.active=rule.active();
        e.updatedAt=LocalDateTime.now();
        return e;
    }

    public UpsellRuleRepository.Rule toDomain(){
        return new UpsellRuleRepository.Rule(
                BusinessId.from(businessId),
                ServiceId.from(baseServiceId),
                ServiceId.from(addonServiceId),
                active);
    }

    public static class Key implements java.io.Serializable{
        public UUID businessId;
        public UUID baseServiceId;
        public Key(){}
        public Key(UUID businessId,UUID baseServiceId){this.businessId=businessId;this.baseServiceId=baseServiceId;}
        @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Key k))return false;return java.util.Objects.equals(businessId,k.businessId)&&java.util.Objects.equals(baseServiceId,k.baseServiceId);}
        @Override public int hashCode(){return java.util.Objects.hash(businessId,baseServiceId);}
    }

    public UUID getBusinessId(){return businessId;}
}
