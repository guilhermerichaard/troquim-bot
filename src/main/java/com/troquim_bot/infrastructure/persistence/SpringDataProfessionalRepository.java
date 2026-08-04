package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.professional.ProfessionalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data de profissionais.
 *
 * Todas as consultas carregam {@code businessId}, pelo mesmo motivo do catálogo.
 */
public interface SpringDataProfessionalRepository extends JpaRepository<ProfessionalJpaEntity, UUID> {

    Optional<ProfessionalJpaEntity> findByBusinessIdAndId(UUID businessId, UUID id);

    List<ProfessionalJpaEntity> findByBusinessId(UUID businessId);

    List<ProfessionalJpaEntity> findByBusinessIdAndStatus(UUID businessId, ProfessionalStatus status);

    /**
     * Profissionais do negócio habilitados para um serviço, pelo vínculo por id.
     *
     * O filtro de status NÃO é feito aqui de propósito: quem decide se um profissional
     * atende é o agregado ({@code Professional.atende}). Esta consulta apenas restringe ao
     * tenant e ao vínculo, para não criar uma segunda fonte de verdade da mesma regra.
     */
    @Query("select p from ProfessionalJpaEntity p "
            + "where p.businessId = :businessId and :servicoId member of p.servicosHabilitados")
    List<ProfessionalJpaEntity> findHabilitadosParaServico(@Param("businessId") UUID businessId,
                                                           @Param("servicoId") UUID servicoId);

    void deleteByBusinessIdAndId(UUID businessId, UUID id);
}
