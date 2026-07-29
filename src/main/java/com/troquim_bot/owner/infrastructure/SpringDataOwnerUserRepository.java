package com.troquim_bot.owner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOwnerUserRepository extends JpaRepository<OwnerUserJpaEntity, UUID> {
    Optional<OwnerUserJpaEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}
