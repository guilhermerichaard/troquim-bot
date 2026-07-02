package com.troquim_bot.repository;

import org.springframework.stereotype.Repository;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do BusinessRepository.
 * MVP assume apenas 1 salão (1 Business).
 */
@Repository
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

    @Override
    public List<Business> findAll() {
        return new ArrayList<>(businesses.values());
    }

    @Override
    public void delete(BusinessId id) {
        if (id != null) {
            businesses.remove(id);
        }
    }
}