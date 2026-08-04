package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.PublicationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data do perfil público. Consulta pública é sempre por (slug, status). */
public interface SpringDataBusinessPublicProfileRepository
        extends JpaRepository<BusinessPublicProfileJpaEntity, UUID> {

    Optional<BusinessPublicProfileJpaEntity> findBySlugAndPublicationStatus(String slug, PublicationStatus status);

    boolean existsBySlug(String slug);
}
