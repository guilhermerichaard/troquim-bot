package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data do expediente. TODA consulta é por {@code businessId} — não existe aqui
 * nenhum método que enxergue mais de um negócio.
 */
public interface SpringDataBusinessHoursRepository extends JpaRepository<BusinessHoursJpaEntity, UUID> {

    List<BusinessHoursJpaEntity> findByBusinessId(UUID businessId);

    void deleteByBusinessId(UUID businessId);

    boolean existsByBusinessId(UUID businessId);
}
