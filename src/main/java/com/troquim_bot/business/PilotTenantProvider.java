package com.troquim_bot.business;

import org.springframework.stereotype.Component;

/**
 * Resolução do tenant para o MVP: o BusinessId do negócio piloto vem de
 * {@link TenantProperties} (configuração tipada, fonte única). Não há UUID
 * literal aqui.
 *
 * Ponto único e substituível: quando o produto virar multi-tenant, troca-se
 * esta implementação por uma que resolve o tenant a partir do canal/autenticação,
 * sem tocar em Controllers/Application.
 */
@Component
public class PilotTenantProvider implements TenantProvider {

    private final BusinessId pilotBusinessId;

    public PilotTenantProvider(TenantProperties properties) {
        if (properties.getPilotBusinessId() == null) {
            throw new IllegalStateException(
                    "troquim.tenant.pilot-business-id é obrigatório e não foi configurado");
        }
        this.pilotBusinessId = BusinessId.from(properties.getPilotBusinessId());
    }

    @Override
    public BusinessId currentBusinessId() {
        return pilotBusinessId;
    }
}
