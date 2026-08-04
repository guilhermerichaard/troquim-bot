package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.repository.BusinessRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica o perfil já configurado: DRAFT → PUBLISHED. IDEMPOTENTE — publicar um perfil já
 * publicado apenas confirma o estado, não é erro.
 */
@Component
public class PublicarPerfilPublico {

    private final BusinessRepository businessRepository;
    private final BusinessPublicProfileRepository perfilRepository;

    public PublicarPerfilPublico(BusinessRepository businessRepository,
                                 BusinessPublicProfileRepository perfilRepository) {
        this.businessRepository = businessRepository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional
    public BusinessPublicProfile publicar(BusinessId businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para publicar o perfil público");
        }

        Business negocio = businessRepository.findById(businessId);
        if (negocio == null) {
            throw new IllegalStateException("Negócio " + businessId + " não está cadastrado");
        }
        // ATIVO ou TRIAL — o mesmo critério de Business#isAtivo(): inativo, suspenso ou
        // deletado não pode expor perfil público.
        if (!negocio.isAtivo()) {
            throw new IllegalStateException(
                    "Negócio " + businessId + " não pode publicar perfil público no status "
                            + negocio.getStatus());
        }

        BusinessPublicProfile perfil = perfilRepository.buscarPorBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum perfil público configurado para " + businessId
                                + "; configure com ConfigurarPerfilPublico antes de publicar"));

        perfil.publicar();
        return perfilRepository.salvar(perfil);
    }
}
