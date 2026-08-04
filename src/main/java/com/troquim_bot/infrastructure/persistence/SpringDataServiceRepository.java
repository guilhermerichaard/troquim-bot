package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.service.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data do catálogo de serviços.
 *
 * Todas as consultas carregam {@code businessId}: não existe derivação por id sozinho, para
 * que nenhuma chamada consiga ler catálogo de outro negócio nem por engano.
 */
public interface SpringDataServiceRepository extends JpaRepository<ServiceJpaEntity, UUID> {

    Optional<ServiceJpaEntity> findByBusinessIdAndId(UUID businessId, UUID id);

    List<ServiceJpaEntity> findByBusinessId(UUID businessId);

    List<ServiceJpaEntity> findByBusinessIdAndStatus(UUID businessId, ServiceStatus status);

    void deleteByBusinessIdAndId(UUID businessId, UUID id);
}
