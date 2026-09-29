package com.troquim_bot.owner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataOwnerOtpChallengeRepository extends JpaRepository<OwnerOtpChallengeJpaEntity, UUID> {
    long countByPhoneHashAndCreatedAtAfter(String phoneHash, LocalDateTime createdAt);
    List<OwnerOtpChallengeJpaEntity> findAllByOwnerIdAndBusinessIdAndPurposeAndConsumedAtIsNull(UUID ownerId, UUID businessId, String purpose);
}
