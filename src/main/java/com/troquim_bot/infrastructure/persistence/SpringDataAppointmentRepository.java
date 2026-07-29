package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Interface Spring Data JPA para AppointmentJpaEntity.
 *
 * Camada de infraestrutura pura. Não deve ser usada diretamente
 * pelo domínio — usar JpaAppointmentRepository (que implementa AppointmentRepository).
 *
 * As consultas de agenda são SEMPRE escopadas por business_id: o professional_id do
 * catálogo do Flow é sintético e compartilhado entre negócios, então filtrar só por
 * profissional cruzaria agendas de tenants diferentes.
 */
@Repository
public interface SpringDataAppointmentRepository extends JpaRepository<AppointmentJpaEntity, UUID> {

    List<AppointmentJpaEntity> findByBusinessIdAndProfessionalIdAndDate(
            UUID businessId, UUID professionalId, LocalDate date);

    List<AppointmentJpaEntity> findByBusinessId(UUID businessId);
}
