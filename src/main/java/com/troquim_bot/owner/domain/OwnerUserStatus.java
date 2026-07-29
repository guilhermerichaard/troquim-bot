package com.troquim_bot.owner.domain;

/** Só ATIVO pode autenticar. SUSPENSO existe para revogar acesso sem apagar o dono. */
public enum OwnerUserStatus {
    ATIVO,
    SUSPENSO
}
