package com.troquim_bot.owner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOwnerPhoneCredentialRepository extends JpaRepository<OwnerPhoneCredentialJpaEntity, UUID> {
    Optional<OwnerPhoneCredentialJpaEntity> findByPhoneE164(String phoneE164);
    Optional<OwnerPhoneCredentialJpaEntity> findByOwnerIdAndBusinessId(UUID ownerId, UUID businessId);
}
