package com.troquim_bot.owner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOwnerSessionRepository extends JpaRepository<OwnerSessionJpaEntity, String> {
    java.util.List<OwnerSessionJpaEntity> findAllByOwnerIdAndBusinessId(java.util.UUID ownerId, java.util.UUID businessId);
}
