package com.troquim_bot.repository;

import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do ServiceRepository.
 */
public class InMemoryServiceRepository implements ServiceRepository {

    private final ConcurrentMap<ServiceId, Service> services = new ConcurrentHashMap<>();

    @Override
    public Service save(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("Service não pode ser nulo");
        }
        services.put(service.getId(), service);
        return service;
    }

    @Override
    public Service findById(ServiceId id) {
        if (id == null) {
            return null;
        }
        return services.get(id);
    }

    @Override
    public boolean exists(ServiceId id) {
        if (id == null) {
            return false;
        }
        return services.containsKey(id);
    }

    @Override
    public List<Service> findAll() {
        return new ArrayList<>(services.values());
    }

    @Override
    public void delete(ServiceId id) {
        if (id != null) {
            services.remove(id);
        }
    }
}