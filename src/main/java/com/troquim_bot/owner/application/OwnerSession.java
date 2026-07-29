package com.troquim_bot.owner.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.domain.OwnerUserId;

import java.time.LocalDateTime;

/**
 * Sessão do dono. Persistida por HASH do token (nunca o token cru), então um vazamento do
 * banco não entrega sessões válidas. Revogável (logout apaga a linha) e expira sozinha.
 */
public record OwnerSession(String tokenHash,
                           OwnerUserId ownerId,
                           BusinessId businessId,
                           LocalDateTime criadaEm,
                           LocalDateTime expiraEm) {

    public boolean utilizavel(LocalDateTime agora) {
        return expiraEm != null && agora.isBefore(expiraEm);
    }

    public AuthenticatedOwner comoIdentidade() {
        return new AuthenticatedOwner(ownerId, businessId);
    }
}
