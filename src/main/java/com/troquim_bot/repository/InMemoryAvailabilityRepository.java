package com.troquim_bot.repository;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplo em memória da disponibilidade profissional.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}. Fora desses perfis a aplicação
 * recusa subir com ele (ver {@code CatalogoPersistenceGuard}): expediente que some a cada
 * restart é pior do que expediente ausente, porque a agenda parece funcionar.
 *
 * A chave é sempre o PAR (negócio, id). Guardar só o id reproduziria em memória exatamente o
 * vazamento entre tenants que a porta existe para impedir.
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryAvailabilityRepository implements AvailabilityRepository {

    private final Map<Chave, Availability> armazenamento = new ConcurrentHashMap<>();

    private record Chave(BusinessId businessId, AvailabilityId id) {
    }

    @Override
    public Availability salvar(Availability availability) {
        if (availability == null) {
            throw new IllegalArgumentException("Availability não pode ser nula");
        }
        armazenamento.put(new Chave(availability.getBusinessId(), availability.getId()), availability);
        return availability;
    }

    @Override
    public Optional<Availability> buscarPorId(BusinessId businessId, AvailabilityId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(armazenamento.get(new Chave(businessId, id)));
    }

    @Override
    public boolean existe(BusinessId businessId, AvailabilityId id) {
        return buscarPorId(businessId, id).isPresent();
    }

    @Override
    public List<Availability> listarPorNegocio(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        List<Availability> encontradas = new ArrayList<>();
        armazenamento.forEach((chave, valor) -> {
            if (chave.businessId().equals(businessId)) {
                encontradas.add(valor);
            }
        });
        return List.copyOf(encontradas);
    }

    @Override
    public List<Availability> listarPorProfissional(BusinessId businessId, ProfessionalId professionalId) {
        if (professionalId == null) {
            return List.of();
        }
        return listarPorNegocio(businessId).stream()
                .filter(a -> a.getProfessionalId().equals(professionalId))
                .toList();
    }

    @Override
    public List<Availability> listarAtivasPorProfissionalEDia(BusinessId businessId,
                                                               ProfessionalId professionalId,
                                                               DiaSemana dayOfWeek) {
        if (dayOfWeek == null) {
            return List.of();
        }
        return listarPorProfissional(businessId, professionalId).stream()
                .filter(Availability::isAtivo)
                .filter(a -> a.getDayOfWeek() == dayOfWeek)
                .toList();
    }

    @Override
    public void remover(BusinessId businessId, AvailabilityId id) {
        if (businessId == null || id == null) {
            return;
        }
        armazenamento.remove(new Chave(businessId, id));
    }
}
