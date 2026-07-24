package com.troquim_bot.repository;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do AvailabilityRepository.
 *
 * Registrado como bean: {@link com.troquim_bot.application.availability.AvailabilityApplicationService}
 * passou a receber suas dependências por injeção (antes o Spring escolhia o construtor
 * vazio e o serviço criava instâncias próprias). Sendo a única implementação existente,
 * é ela que satisfaz a porta — e agora há UMA instância compartilhada, não uma por
 * serviço.
 */
@Repository
public class InMemoryAvailabilityRepository implements AvailabilityRepository {

    private final ConcurrentMap<AvailabilityId, Availability> availabilities = new ConcurrentHashMap<>();

    @Override
    public Availability save(Availability availability) {
        if (availability == null) {
            throw new IllegalArgumentException("Availability não pode ser nulo");
        }
        availabilities.put(availability.getId(), availability);
        return availability;
    }

    @Override
    public Availability findById(AvailabilityId id) {
        if (id == null) {
            return null;
        }
        return availabilities.get(id);
    }

    @Override
    public boolean exists(AvailabilityId id) {
        if (id == null) {
            return false;
        }
        return availabilities.containsKey(id);
    }

    @Override
    public List<Availability> findAll() {
        return new ArrayList<>(availabilities.values());
    }

    @Override
    public List<Availability> findByProfessionalId(ProfessionalId professionalId) {
        if (professionalId == null) {
            return List.of();
        }
        return availabilities.values().stream()
            .filter(a -> professionalId.equals(a.getProfessionalId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Availability> findByProfessionalIdAndDayOfWeek(ProfessionalId professionalId, DiaSemana dayOfWeek) {
        if (professionalId == null || dayOfWeek == null) {
            return List.of();
        }
        return availabilities.values().stream()
            .filter(a -> professionalId.equals(a.getProfessionalId()) && dayOfWeek == a.getDayOfWeek())
            .collect(Collectors.toList());
    }

    @Override
    public void delete(AvailabilityId id) {
        if (id != null) {
            availabilities.remove(id);
        }
    }
}