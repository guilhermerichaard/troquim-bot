package com.troquim_bot.application.business;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.BusinessPublicProfileRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Consulta ADMINISTRATIVA do perfil público, em qualquer status — o dono vendo o próprio
 * rascunho antes de publicar. Não confundir com {@link ConsultarPerfilPublicadoPorSlug}, que
 * é a consulta PÚBLICA e só devolve o que está PUBLISHED.
 */
@Component
public class ConsultarPerfilPublicoPorBusinessId {

    private final BusinessPublicProfileRepository perfilRepository;

    public ConsultarPerfilPublicoPorBusinessId(BusinessPublicProfileRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BusinessPublicProfile> consultar(BusinessId businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return perfilRepository.buscarPorBusinessId(businessId);
    }
}
