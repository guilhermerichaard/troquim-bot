package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter JPA da disponibilidade profissional — o repositório de PRODUÇÃO.
 *
 * Só converte e persiste. Sobreposição de períodos, ciclo de vida e pertencimento ao negócio
 * são decisões do agregado; aqui não há nenhuma.
 */
@Repository
@Primary
public class JpaAvailabilityRepository implements AvailabilityRepository {

    private final SpringDataAvailabilityRepository springDataRepository;

    public JpaAvailabilityRepository(SpringDataAvailabilityRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Availability salvar(Availability availability) {
        if (availability == null) {
            throw new IllegalArgumentException("Availability não pode ser nula");
        }
        springDataRepository.save(AvailabilityJpaEntity.de(availability));
        return availability;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Availability> buscarPorId(BusinessId businessId, AvailabilityId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return springDataRepository.findByBusinessIdAndId(businessId.getValue(), id.getValue())
                .map(AvailabilityJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existe(BusinessId businessId, AvailabilityId id) {
        return businessId != null && id != null
                && springDataRepository.existsByBusinessIdAndId(businessId.getValue(), id.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Availability> listarPorNegocio(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository.findByBusinessId(businessId.getValue()).stream()
                .map(AvailabilityJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Availability> listarPorProfissional(BusinessId businessId, ProfessionalId professionalId) {
        if (businessId == null || professionalId == null) {
            return List.of();
        }
        return springDataRepository
                .findByBusinessIdAndProfessionalId(businessId.getValue(), professionalId.getValue())
                .stream()
                .map(AvailabilityJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Availability> listarAtivasPorProfissionalEDia(BusinessId businessId,
                                                               ProfessionalId professionalId,
                                                               DiaSemana dayOfWeek) {
        if (businessId == null || professionalId == null || dayOfWeek == null) {
            return List.of();
        }
        return springDataRepository
                .findByBusinessIdAndProfessionalIdAndDiaSemanaAndStatus(
                        businessId.getValue(), professionalId.getValue(), dayOfWeek,
                        AvailabilityStatus.ATIVO)
                .stream()
                .map(AvailabilityJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public void remover(BusinessId businessId, AvailabilityId id) {
        if (businessId == null || id == null) {
            return;
        }
        // Escopado por tenant: id de outro negócio simplesmente não casa.
        springDataRepository.deleteByBusinessIdAndId(businessId.getValue(), id.getValue());
    }
}
