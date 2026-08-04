package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.BusinessRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapter JPA de Business — o repositório de PRODUÇÃO.
 *
 * Só converte e persiste. {@code save} é UPSERT: como {@link BusinessJpaEntity} sempre tem
 * o id já atribuído, o merge do JPA atualiza a linha existente ou insere a nova — nunca
 * duplica.
 */
@Repository
@Primary
public class JpaBusinessRepository implements BusinessRepository {

    private final SpringDataBusinessRepository springDataRepository;

    public JpaBusinessRepository(SpringDataBusinessRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Business save(Business business) {
        if (business == null) {
            throw new IllegalArgumentException("Business não pode ser nulo");
        }
        BusinessJpaEntity salvo = springDataRepository.save(BusinessJpaEntity.de(business));
        return salvo.paraDominio();
    }

    @Override
    @Transactional(readOnly = true)
    public Business findById(BusinessId id) {
        if (id == null) {
            return null;
        }
        return springDataRepository.findById(id.getValue())
                .map(BusinessJpaEntity::paraDominio)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(BusinessId id) {
        return id != null && springDataRepository.existsById(id.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Business> findAll() {
        return springDataRepository.findAll().stream()
                .map(BusinessJpaEntity::paraDominio)
                .toList();
    }
}
