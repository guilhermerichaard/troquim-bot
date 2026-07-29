package com.troquim_bot.owner.application;

import java.util.Optional;

/** Porta de persistência das sessões. Chave = hash do token. */
public interface OwnerSessionStore {

    OwnerSession criar(OwnerSession session);

    Optional<OwnerSession> buscarPorTokenHash(String tokenHash);

    void revogarPorTokenHash(String tokenHash);
}
