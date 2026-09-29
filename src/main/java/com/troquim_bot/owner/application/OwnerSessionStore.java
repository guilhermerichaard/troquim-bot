package com.troquim_bot.owner.application;

import java.util.Optional;
import java.util.List;
import com.troquim_bot.owner.domain.OwnerUserId;
import com.troquim_bot.business.BusinessId;

/** Porta de persistência das sessões. Chave = hash do token. */
public interface OwnerSessionStore {

    OwnerSession criar(OwnerSession session);

    Optional<OwnerSession> buscarPorTokenHash(String tokenHash);

    void revogarPorTokenHash(String tokenHash);

    List<OwnerSession> listarDoDono(OwnerUserId ownerId, BusinessId businessId);
}
