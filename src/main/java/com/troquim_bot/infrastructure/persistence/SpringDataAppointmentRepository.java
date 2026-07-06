package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Interface Spring Data JPA para AppointmentJpaEntity.
 * 
 * Camada de infraestrutura pura. Não deve ser usada diretamente
 * pelo domínio — usar JpaAppointmentRepository (que implementa AppointmentRepository).
 */
@Repository
public interface SpringDataAppointmentRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
}