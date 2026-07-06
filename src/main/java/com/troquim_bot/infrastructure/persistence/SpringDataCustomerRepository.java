package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Interface Spring Data JPA para CustomerJpaEntity.
 * 
 * Camada de infraestrutura pura. Não deve ser usada diretamente
 * pelo domínio — usar JpaCustomerRepository (que implementa CustomerRepository).
 */
@Repository
public interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {
}