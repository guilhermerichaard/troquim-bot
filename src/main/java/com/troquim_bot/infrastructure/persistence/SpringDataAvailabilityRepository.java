package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data da disponibilidade profissional.
 *
 * TODO método recebe {@code businessId} — inclusive a busca por id e a remoção. Não existe
 * aqui nenhuma consulta capaz de enxergar mais de um negócio: o vazamento de tenant deixa de
 * ser possível por construção, e não por disciplina de quem chama.
 */
public interface SpringDataAvailabilityRepository extends JpaRepository<AvailabilityJpaEntity, UUID> {

    Optional<AvailabilityJpaEntity> findByBusinessIdAndId(UUID businessId, UUID id);

    boolean existsByBusinessIdAndId(UUID businessId, UUID id);

    List<AvailabilityJpaEntity> findByBusinessId(UUID businessId);

    List<AvailabilityJpaEntity> findByBusinessIdAndProfessionalId(UUID businessId, UUID professionalId);

    List<AvailabilityJpaEntity> findByBusinessIdAndProfessionalIdAndDiaSemanaAndStatus(
            UUID businessId, UUID professionalId, DiaSemana diaSemana, AvailabilityStatus status);

    void deleteByBusinessIdAndId(UUID businessId, UUID id);
}
