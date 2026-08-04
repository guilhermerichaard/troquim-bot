package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.professional.ProfessionalStatus;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.service.ServiceId;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter JPA de profissionais — o repositório de PRODUÇÃO.
 *
 * Só converte e persiste. A regra de habilitação continua no agregado: a consulta traz os
 * vinculados ao serviço e o filtro final usa {@code Professional.atende}, para não existir
 * uma segunda implementação da mesma decisão dentro de uma query.
 */
@Repository
@Primary
public class JpaProfessionalRepository implements ProfessionalRepository {

    private final SpringDataProfessionalRepository springDataRepository;

    public JpaProfessionalRepository(SpringDataProfessionalRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Professional salvar(Professional professional) {
        if (professional == null) {
            throw new IllegalArgumentException("Professional não pode ser nulo");
        }
        springDataRepository.save(ProfessionalJpaEntity.de(professional));
        return professional;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Professional> buscarPorId(BusinessId businessId, ProfessionalId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return springDataRepository
                .findByBusinessIdAndId(businessId.getValue(), id.getValue())
                .map(ProfessionalJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Professional> listarAtivos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository
                .findByBusinessIdAndStatus(businessId.getValue(), ProfessionalStatus.ATIVO)
                .stream()
                .map(ProfessionalJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Professional> listarAtivosPorServico(BusinessId businessId, ServiceId servico) {
        if (businessId == null || servico == null) {
            return List.of();
        }
        return springDataRepository
                .findHabilitadosParaServico(businessId.getValue(), servico.getValue())
                .stream()
                .map(ProfessionalJpaEntity::paraDominio)
                // Quem decide se atende é o agregado — inclusive a regra de inativo.
                .filter(professional -> professional.atende(servico))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Professional> listarTodos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository.findByBusinessId(businessId.getValue()).stream()
                .map(ProfessionalJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public void remover(BusinessId businessId, ProfessionalId id) {
        if (businessId == null || id == null) {
            return;
        }
        springDataRepository.deleteByBusinessIdAndId(businessId.getValue(), id.getValue());
    }
}
