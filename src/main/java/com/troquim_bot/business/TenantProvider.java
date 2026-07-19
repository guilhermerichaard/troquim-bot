package com.troquim_bot.business;

/**
 * Porta de resolução do tenant corrente.
 *
 * No MVP há um único negócio (piloto), então a resolução é uma configuração
 * explícita e centralizada (ver {@link PilotTenantProvider}). A abstração
 * existe para ser substituída quando o tenant passar a vir do canal/autenticação
 * (multi-tenant), sem espalhar UUID hardcoded por Controllers e serviços.
 */
public interface TenantProvider {

    /**
     * Retorna o BusinessId do tenant corrente.
     */
    BusinessId currentBusinessId();
}
