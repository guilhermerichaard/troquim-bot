package com.troquim_bot.application.business;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.BusinessPublicProfileRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Despublica o perfil: PUBLISHED → DRAFT. IDEMPOTENTE — despublicar um perfil já em DRAFT
 * apenas confirma o estado, não é erro.
 */
@Component
public class DespublicarPerfilPublico {

    private final BusinessPublicProfileRepository perfilRepository;

    public DespublicarPerfilPublico(BusinessPublicProfileRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    @Transactional
    public BusinessPublicProfile despublicar(BusinessId businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para despublicar o perfil público");
        }

        BusinessPublicProfile perfil = perfilRepository.buscarPorBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum perfil público configurado para " + businessId));

        perfil.despublicar();
        return perfilRepository.salvar(perfil);
    }
}
