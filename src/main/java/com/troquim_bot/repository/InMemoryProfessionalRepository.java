package com.troquim_bot.repository;

import org.springframework.stereotype.Repository;

import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do ProfessionalRepository.
 */
@Repository
public class InMemoryProfessionalRepository implements ProfessionalRepository {

    private final ConcurrentMap<ProfessionalId, Professional> professionals = new ConcurrentHashMap<>();

    @Override
    public Professional save(Professional professional) {
        if (professional == null) {
            throw new IllegalArgumentException("Professional não pode ser nulo");
        }
        professionals.put(professional.getId(), professional);
        return professional;
    }

    @Override
    public Professional findById(ProfessionalId id) {
        if (id == null) {
            return null;
        }
        return professionals.get(id);
    }

    @Override
    public boolean exists(ProfessionalId id) {
        if (id == null) {
            return false;
        }
        return professionals.containsKey(id);
    }

    @Override
    public List<Professional> findAll() {
        return new ArrayList<>(professionals.values());
    }

    @Override
    public void delete(ProfessionalId id) {
        if (id != null) {
            professionals.remove(id);
        }
    }
}