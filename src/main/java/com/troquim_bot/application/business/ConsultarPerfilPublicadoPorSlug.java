package com.troquim_bot.application.business;

import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.repository.BusinessPublicProfileRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Consulta PÚBLICA por slug — a fronteira que uma futura página pública vai chamar. Devolve
 * SOMENTE perfil PUBLISHED: um DRAFT com este slug é, para este caso de uso, indistinguível
 * de "não existe". Nenhum dado interno de {@link com.troquim_bot.business.Business} é
 * exposto — só o que o próprio {@link BusinessPublicProfile} guarda.
 *
 * Um slug bruto que não passa nem na normalização (formato inválido) não pode corresponder a
 * nenhum perfil publicado — devolve vazio sem consultar o banco.
 */
@Component
public class ConsultarPerfilPublicadoPorSlug {

    private final BusinessPublicProfileRepository perfilRepository;

    public ConsultarPerfilPublicadoPorSlug(BusinessPublicProfileRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BusinessPublicProfile> consultar(String slugBruto) {
        BusinessSlug slug;
        try {
            slug = BusinessSlug.normalizarDe(slugBruto);
        } catch (IllegalArgumentException slugInvalido) {
            return Optional.empty();
        }
        return perfilRepository.buscarPublicadoPorSlug(slug);
    }
}
