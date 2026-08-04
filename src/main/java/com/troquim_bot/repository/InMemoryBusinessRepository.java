package com.troquim_bot.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do BusinessRepository.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}; fora deles a guarda de
 * persistência derruba o startup em vez de deixar o Business evaporar a cada restart.
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryBusinessRepository implements BusinessRepository {

    private final ConcurrentMap<BusinessId, Business> businesses = new ConcurrentHashMap<>();

    @Override
    public Business save(Business business) {
        if (business == null) {
            throw new IllegalArgumentException("Business não pode ser nulo");
        }
        businesses.put(business.getId(), business);
        return business;
    }

    @Override
    public Business findById(BusinessId id) {
        if (id == null) {
            return null;
        }
        return businesses.get(id);
    }

    @Override
    public boolean exists(BusinessId id) {
        if (id == null) {
            return false;
        }
        return businesses.containsKey(id);
    }
}
