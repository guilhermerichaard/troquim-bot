package com.troquim_bot.application.business;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.repository.BusinessRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de configuração do perfil público — cria ou atualiza SOMENTE o perfil do
 * {@link BusinessId} informado. Nunca publica sozinho: publicar é decisão de
 * {@link PublicarPerfilPublico}, um caso de uso à parte.
 *
 * ENTRADA MÍNIMA: BusinessId e slug EXPLÍCITOS. Nenhuma dedução de tenant por sessão,
 * Controller ou IA — quem chama prova de qual negócio está configurando.
 */
@Component
public class ConfigurarPerfilPublico {

    private final BusinessRepository businessRepository;
    private final BusinessPublicProfileRepository perfilRepository;

    public ConfigurarPerfilPublico(BusinessRepository businessRepository,
                                   BusinessPublicProfileRepository perfilRepository) {
        this.businessRepository = businessRepository;
        this.perfilRepository = perfilRepository;
    }

    /**
     * @throws SlugIndisponivelException se o slug já pertencer a outro negócio
     */
    @Transactional
    public BusinessPublicProfile configurar(BusinessId businessId, String slugBruto, String nomePublico,
                                            String descricaoCurta, String telefonePublico,
                                            String enderecoPublico) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para configurar o perfil público");
        }
        if (!businessRepository.exists(businessId)) {
            throw new IllegalStateException(
                    "Negócio " + businessId + " não está cadastrado; cadastre-o com "
                            + "CadastrarNegocio antes de configurar o perfil público");
        }

        BusinessSlug slug = BusinessSlug.normalizarDe(slugBruto);

        BusinessPublicProfile perfil = perfilRepository.buscarPorBusinessId(businessId).orElse(null);
        if (perfil == null) {
            perfil = new BusinessPublicProfile(businessId, slug, nomePublico, descricaoCurta,
                    telefonePublico, enderecoPublico);
        } else {
            perfil.atualizarConfiguracao(slug, nomePublico, descricaoCurta, telefonePublico, enderecoPublico);
        }

        return perfilRepository.salvar(perfil);
    }
}
