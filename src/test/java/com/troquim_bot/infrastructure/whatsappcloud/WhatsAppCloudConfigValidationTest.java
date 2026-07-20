package com.troquim_bot.infrastructure.whatsappcloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validação fail-fast da configuração da integração (cenários 33-35). Carrega apenas
 * a fiação da integração via {@link ApplicationContextRunner} — sem datasource nem app
 * completa — e ativa o profile azure por inicializador.
 */
@DisplayName("WhatsApp Cloud - validacao de configuracao (fail-fast)")
class WhatsAppCloudConfigValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(azureProfile())
            .withUserConfiguration(WhatsAppCloudConfiguration.class, WhatsAppCloudConfigValidator.class);

    @Test
    @DisplayName("33. azure + enabled sem credenciais falha cedo")
    void azureEnabledSemCredenciaisFalha() {
        runner.withPropertyValues("troquim.integrations.whatsapp.cloud.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasStackTraceContaining("deve ser configurada"));
    }

    @Test
    @DisplayName("34. azure + enabled com whitespace em segredo falha cedo")
    void azureEnabledWhitespaceFalha() {
        // Injeta o valor EXATO com newline via MapPropertySource (withPropertyValues trima).
        new ApplicationContextRunner()
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("azure");
                    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                            "whatsappCloudUnderTest", Map.of(
                                    "troquim.integrations.whatsapp.cloud.enabled", "true",
                                    "troquim.integrations.whatsapp.cloud.verify-token", "vt",
                                    "troquim.integrations.whatsapp.cloud.app-secret", "secret-with-newline\n",
                                    "troquim.integrations.whatsapp.cloud.access-token", "at",
                                    "troquim.integrations.whatsapp.cloud.phone-number-id", "pnid",
                                    "troquim.integrations.whatsapp.cloud.graph-api-version", "vtest")));
                })
                .withUserConfiguration(WhatsAppCloudConfiguration.class, WhatsAppCloudConfigValidator.class)
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasStackTraceContaining("whitespace"));
    }

    @Test
    @DisplayName("35. disabled inicia sem credenciais")
    void disabledIniciaSemCredenciais() {
        runner.withPropertyValues("troquim.integrations.whatsapp.cloud.enabled=false")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("controle: azure + enabled com credenciais limpas inicia OK")
    void azureEnabledCredenciaisLimpasOk() {
        runner.withPropertyValues(
                        "troquim.integrations.whatsapp.cloud.enabled=true",
                        "troquim.integrations.whatsapp.cloud.verify-token=vt",
                        "troquim.integrations.whatsapp.cloud.app-secret=as",
                        "troquim.integrations.whatsapp.cloud.access-token=at",
                        "troquim.integrations.whatsapp.cloud.phone-number-id=pnid",
                        "troquim.integrations.whatsapp.cloud.graph-api-version=vtest")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> azureProfile() {
        return ctx -> ctx.getEnvironment().setActiveProfiles("azure");
    }
}
