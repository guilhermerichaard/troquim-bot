package com.troquim_bot.owner.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.domain.OwnerUserId;

/**
 * Identidade já autenticada de um dono. É o que o controller do /app extrai da sessão e
 * repassa à Application — o businessId daqui é a ÚNICA fonte de tenant do /app, no lugar
 * do PilotTenantProvider.
 */
public record AuthenticatedOwner(OwnerUserId ownerId, BusinessId businessId) {

    public AuthenticatedOwner {
        if (ownerId == null) throw new IllegalArgumentException("ownerId é obrigatório");
        if (businessId == null) throw new IllegalArgumentException("businessId é obrigatório");
    }
}
