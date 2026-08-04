package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.BusinessRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Fronteira ÚNICA entre um slug público e o {@link BusinessId} interno.
 *
 * RESPONSABILIDADE ÚNICA: transformar "slug" em "BusinessId", ou em nada. Todo outro caso de
 * uso público (vitrine, disponibilidade) resolve o negócio por AQUI, nunca lendo
 * {@code BusinessPublicProfileRepository} ou {@code BusinessRepository} diretamente — é o
 * que garante que perfil DRAFT, slug inexistente e negócio INATIVO/SUSPENSO/DELETADO
 * pareçam TODOS igualmente inexistentes para quem consulta de fora.
 *
 * NÃO EXPÕE {@link Business}: o resultado carrega só o {@link BusinessId} e o
 * {@link BusinessPublicProfile} (que já é, por natureza, dado seguro para expor). Nenhum
 * dado administrativo do Business — status interno, telefone privado, timestamps — atravessa
 * esta fronteira.
 */
@Component
public class ResolverNegocioPublicoPorSlug {

    private final ConsultarPerfilPublicadoPorSlug consultarPerfilPublicadoPorSlug;
    private final BusinessRepository businessRepository;

    public ResolverNegocioPublicoPorSlug(ConsultarPerfilPublicadoPorSlug consultarPerfilPublicadoPorSlug,
                                         BusinessRepository businessRepository) {
        this.consultarPerfilPublicadoPorSlug = consultarPerfilPublicadoPorSlug;
        this.businessRepository = businessRepository;
    }

    /** Negócio público resolvido: o suficiente para qualquer caso de uso público continuar. */
    public record NegocioPublico(BusinessId businessId, BusinessPublicProfile perfil) {
    }

    /**
     * Vazio quando: o slug é inválido, não existe, o perfil está em DRAFT, o negócio não foi
     * encontrado, ou o negócio não está ATIVO/TRIAL. Todos esses casos são o MESMO vazio —
     * quem chama não distingue um do outro, de propósito.
     */
    @Transactional(readOnly = true)
    public Optional<NegocioPublico> resolver(String slugBruto) {
        return consultarPerfilPublicadoPorSlug.consultar(slugBruto)
                .flatMap(this::comNegocioAtivo);
    }

    private Optional<NegocioPublico> comNegocioAtivo(BusinessPublicProfile perfil) {
        Business negocio = businessRepository.findById(perfil.getBusinessId());
        if (negocio == null || !negocio.isAtivo()) {
            return Optional.empty();
        }
        return Optional.of(new NegocioPublico(perfil.getBusinessId(), perfil));
    }
}
