package com.troquim_bot.owner.application;

import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.domain.OwnerUserId;

import java.util.Optional;

/** Porta de persistência dos donos. Busca por e-mail é a chave de login. */
public interface OwnerUserRepository {

    OwnerUser salvar(OwnerUser owner);

    Optional<OwnerUser> buscarPorEmail(String email);

    Optional<OwnerUser> buscarPorId(OwnerUserId id);

    boolean existePorEmail(String email);
}
