package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data JPA para CustomerJpaEntity.
 *
 * Camada de infraestrutura pura. Não deve ser usada diretamente pelo domínio —
 * usar JpaCustomerRepository (que implementa CustomerRepository).
 *
 * Não expõe listagem global: as consultas de tenant recebem o business_id.
 */
@Repository
public interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {

    Optional<CustomerJpaEntity> findByBusinessIdAndPhoneE164(UUID businessId, String phoneE164);

    List<CustomerJpaEntity> findByBusinessId(UUID businessId);
}
