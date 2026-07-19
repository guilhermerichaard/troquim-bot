package com.troquim_bot.support;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;

import java.util.UUID;

/**
 * Helpers de tenant para testes. Centraliza (no código de TESTE) o BusinessId do
 * piloto — o mesmo default de dev/test em application.properties — para não
 * espalhar UUID pelos testes. Produção não tem UUID literal (ver TenantProperties).
 */
public final class TestTenants {

    private TestTenants() {}

    /** BusinessId canônico do piloto (igual ao default de dev/test). */
    public static final BusinessId PILOT = BusinessId.from(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));

    /** Um segundo tenant, para provar isolamento cross-tenant. */
    public static final BusinessId OUTRO = BusinessId.from(
            UUID.fromString("22222222-2222-2222-2222-222222222222"));

    /** TenantProvider fixo no piloto. */
    public static TenantProvider pilot() {
        return () -> PILOT;
    }

    /** TenantProvider fixo num BusinessId arbitrário. */
    public static TenantProvider of(BusinessId businessId) {
        return () -> businessId;
    }
}
