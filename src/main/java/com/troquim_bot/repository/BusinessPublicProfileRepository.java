package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;

import java.util.Optional;

/**
 * Port de persistência do perfil público do negócio.
 *
 * Um perfil por {@link BusinessId} — {@code salvar} é UPSERT pela mesma identidade.
 *
 * UNICIDADE DO SLUG NÃO É CHECADA AQUI ANTES DE SALVAR: dois negócios configurando o mesmo
 * slug ao mesmo tempo não podem ser resolvidos por um {@code exists} seguido de
 * {@code save} — a corrida passaria os dois pelo {@code exists}. A garantia é do banco
 * (constraint UNIQUE); {@code salvar} PROPAGA um conflito de slug como
 * {@code SlugIndisponivelException} (ver adapter JPA), nunca engole silenciosamente.
 */
public interface BusinessPublicProfileRepository {

    /**
     * Salva o perfil (cria ou atualiza).
     *
     * @throws com.troquim_bot.application.business.SlugIndisponivelException se o slug já
     *         pertencer a outro negócio — só essa violação específica vira esta exceção;
     *         qualquer outro erro de integridade propaga como veio.
     */
    BusinessPublicProfile salvar(BusinessPublicProfile perfil);

    /** Perfil do negócio, em qualquer status de publicação. */
    Optional<BusinessPublicProfile> buscarPorBusinessId(BusinessId businessId);

    /**
     * Perfil PUBLICADO pelo slug. Um perfil em DRAFT com este slug NUNCA é devolvido — para
     * quem consulta publicamente, um rascunho é indistinguível de "não existe".
     */
    Optional<BusinessPublicProfile> buscarPublicadoPorSlug(BusinessSlug slug);

    /**
     * Checagem de conveniência, NÃO AUTORITATIVA: útil para uma resposta rápida de UX, mas
     * não substitui o tratamento do conflito real na escrita (ver {@link #salvar}).
     */
    boolean slugDisponivel(BusinessSlug slug);
}
