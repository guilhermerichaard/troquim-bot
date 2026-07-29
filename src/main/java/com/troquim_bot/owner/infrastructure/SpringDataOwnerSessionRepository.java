package com.troquim_bot.owner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOwnerSessionRepository extends JpaRepository<OwnerSessionJpaEntity, String> {
}
