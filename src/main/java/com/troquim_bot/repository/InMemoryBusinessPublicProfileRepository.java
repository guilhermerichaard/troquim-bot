package com.troquim_bot.repository;

import com.troquim_bot.application.business.SlugIndisponivelException;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.business.PublicationStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplo em memória do perfil público.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}; fora deles a guarda de
 * persistência derruba o startup em vez de deixar o perfil evaporar a cada restart.
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryBusinessPublicProfileRepository implements BusinessPublicProfileRepository {

    private final Map<BusinessId, BusinessPublicProfile> perfis = new ConcurrentHashMap<>();

    @Override
    public synchronized BusinessPublicProfile salvar(BusinessPublicProfile perfil) {
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil é obrigatório");
        }
        boolean slugEmUsoPorOutro = perfis.values().stream()
                .anyMatch(p -> !p.getBusinessId().equals(perfil.getBusinessId())
                        && p.getSlug().equals(perfil.getSlug()));
        if (slugEmUsoPorOutro) {
            throw new SlugIndisponivelException(perfil.getSlug().getValue(), null);
        }
        perfis.put(perfil.getBusinessId(), perfil);
        return perfil;
    }

    @Override
    public Optional<BusinessPublicProfile> buscarPorBusinessId(BusinessId businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(perfis.get(businessId));
    }

    @Override
    public Optional<BusinessPublicProfile> buscarPublicadoPorSlug(BusinessSlug slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return perfis.values().stream()
                .filter(p -> p.getStatus() == PublicationStatus.PUBLISHED)
                .filter(p -> p.getSlug().equals(slug))
                .findFirst();
    }

    @Override
    public boolean slugDisponivel(BusinessSlug slug) {
        if (slug == null) {
            return false;
        }
        return perfis.values().stream().noneMatch(p -> p.getSlug().equals(slug));
    }
}
