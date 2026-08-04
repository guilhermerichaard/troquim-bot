package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data da raiz de identidade do negócio. */
public interface SpringDataBusinessRepository extends JpaRepository<BusinessJpaEntity, UUID> {
}
