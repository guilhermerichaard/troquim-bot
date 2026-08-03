package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.service.ServiceStatus;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter JPA do catálogo de serviços — o repositório de PRODUÇÃO.
 *
 * Anotado com {@code @Primary}: quando o duplo em memória existe (perfis {@code test} e
 * {@code dev-inmemory}), este continua sendo o escolhido para injeção.
 *
 * Só converte e persiste. Nenhuma regra de negócio mora aqui: a decisão de "quem atende o
 * quê" e a de status pertencem ao domínio.
 */
@Repository
@Primary
public class JpaServiceRepository implements ServiceRepository {

    private final SpringDataServiceRepository springDataRepository;

    public JpaServiceRepository(SpringDataServiceRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Service salvar(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("Service não pode ser nulo");
        }
        springDataRepository.save(ServiceJpaEntity.de(service));
        return service;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Service> buscarPorId(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return springDataRepository
                .findByBusinessIdAndId(businessId.getValue(), id.getValue())
                .map(ServiceJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> listarAtivos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository
                .findByBusinessIdAndStatus(businessId.getValue(), ServiceStatus.ATIVO)
                .stream()
                .map(ServiceJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Service> listarTodos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository.findByBusinessId(businessId.getValue()).stream()
                .map(ServiceJpaEntity::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public void remover(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            return;
        }
        // Escopado por tenant: id de outro negócio simplesmente não casa.
        springDataRepository.deleteByBusinessIdAndId(businessId.getValue(), id.getValue());
    }
}
