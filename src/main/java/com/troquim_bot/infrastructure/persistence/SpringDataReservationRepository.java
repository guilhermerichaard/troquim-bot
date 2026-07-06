package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data JPA para ReservationJpaEntity.
 * 
 * Camada de infraestrutura pura. Não deve ser usada diretamente
 * pelo domínio — usar JpaReservationRepository (que implementa ReservationRepository).
 */
@Repository
public interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    /**
     * Busca reservas por ID do profissional e data.
     */
    List<ReservationJpaEntity> findByProfessionalIdAndDate(UUID professionalId, LocalDate date);
}