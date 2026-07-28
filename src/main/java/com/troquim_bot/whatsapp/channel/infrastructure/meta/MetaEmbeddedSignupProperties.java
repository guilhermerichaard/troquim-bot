package com.troquim_bot.whatsapp.channel.infrastructure.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do Embedded Signup (Facebook Login for Business). Prefixo
 * {@code troquim.integrations.whatsapp.embedded-signup}.
 *
 * Separação que importa: {@code appId} e {@code configId} são PÚBLICOS — vão ao
 * navegador para abrir o diálogo da Meta e não valem nada sozinhos. {@code appSecret}
 * é segredo e existe apenas aqui, na Infrastructure, para assinar a troca do code.
 * Nenhum endpoint devolve o secret, e nenhum log o registra.
 */
@ConfigurationProperties(prefix = "troquim.integrations.whatsapp.embedded-signup")
public class MetaEmbeddedSignupProperties {

    /** Liga/desliga o onboarding por Embedded Signup. Default: desligado. */
    private boolean enabled = false;

    /** App ID da aplicação Meta. PÚBLICO — pode ir ao frontend. */
    private String appId;

    /** Config ID do fluxo de Embedded Signup. PÚBLICO — pode ir ao frontend. */
    private String configId;

    /** App Secret. SEGREDO — nunca sai do backend. */
    private String appSecret;

    /** Versão da Graph API — sem default; configuração explícita obrigatória. */
    private String graphApiVersion;

    /** Base URL da Graph API. Default oficial público, sobrescrevível em teste. */
    private String baseUrl = "https://graph.facebook.com";

    /** Tudo que o fluxo precisa está configurado? */
    public boolean configurado() {
        return enabled
                && naoVazio(appId) && naoVazio(configId)
                && naoVazio(appSecret) && naoVazio(graphApiVersion);
    }

    private static boolean naoVazio(String valor) {
        return valor != null && !valor.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getGraphApiVersion() {
        return graphApiVersion;
    }

    public void setGraphApiVersion(String graphApiVersion) {
        this.graphApiVersion = graphApiVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
