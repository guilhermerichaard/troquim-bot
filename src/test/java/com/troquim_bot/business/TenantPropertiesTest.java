package com.troquim_bot.business;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config tipada de tenancy (fonte única do BusinessId do piloto):
 * UUID válido faz bind; UUID inválido faz o contexto falhar (fail-fast) — o mesmo
 * mecanismo que protege azure/prod. A obrigatoriedade em produção (variável
 * ausente) é garantida pelo placeholder sem default em application-azure/prod,
 * exercitado pelos testes Testcontainers com o profile azure.
 */
class TenantPropertiesTest {

    private static final String PILOT = "11111111-1111-1111-1111-111111111111";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TenancyConfiguration.class);

    @Test
    void uuidValidoFazBind() {
        runner.withPropertyValues("troquim.tenant.pilot-business-id=" + PILOT)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(TenantProperties.class).getPilotBusinessId())
                            .isEqualTo(UUID.fromString(PILOT));
                });
    }

    @Test
    void uuidInvalidoFazContextoFalhar() {
        runner.withPropertyValues("troquim.tenant.pilot-business-id=nao-e-um-uuid")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void pilotTenantProviderResolveOBusinessIdConfigurado() {
        runner.withUserConfiguration(PilotTenantProvider.class)
                .withPropertyValues("troquim.tenant.pilot-business-id=" + PILOT)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(PilotTenantProvider.class).currentBusinessId())
                            .isEqualTo(BusinessId.from(UUID.fromString(PILOT)));
                });
    }
}
