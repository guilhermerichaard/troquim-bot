package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpringDataUpsellRuleRepository
        extends JpaRepository<UpsellRuleJpaEntity, UpsellRuleJpaEntity.Key> {
    List<UpsellRuleJpaEntity> findByBusinessId(UUID businessId);
}
