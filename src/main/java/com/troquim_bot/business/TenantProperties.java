package com.troquim_bot.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Configuração tipada de tenancy — FONTE ÚNICA do BusinessId do negócio piloto.
 *
 * O tipo {@link UUID} dá fail-fast tipado: um valor não-UUID em
 * {@code troquim.tenant.pilot-business-id} faz o bind falhar no startup.
 * A obrigatoriedade em produção é garantida pelos profiles azure/prod, que
 * definem a propriedade a partir da variável de ambiente TROQUIM_PILOT_BUSINESS_ID
 * sem default (placeholder não resolvido → startup falha).
 */
@ConfigurationProperties(prefix = "troquim.tenant")
public class TenantProperties {

    private UUID pilotBusinessId;

    public UUID getPilotBusinessId() {
        return pilotBusinessId;
    }

    public void setPilotBusinessId(UUID pilotBusinessId) {
        this.pilotBusinessId = pilotBusinessId;
    }
}
