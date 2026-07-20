package com.troquim_bot.infrastructure.whatsappcloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração tipada da integração oficial com a WhatsApp Cloud API (Meta).
 * Prefixo {@code troquim.integrations.whatsapp.cloud}. Segredos vêm SEMPRE de
 * variáveis de ambiente (nunca versionados). Nenhum valor é registrado em log.
 *
 * {@code baseUrl} tem default oficial público. {@code graphApiVersion} NÃO tem
 * default: a versão da Graph API deve ser configuração explícita (não presumir).
 * A validação fail-fast (azure + enabled) vive em {@link WhatsAppCloudConfiguration}.
 */
@ConfigurationProperties(prefix = "troquim.integrations.whatsapp.cloud")
public class WhatsAppCloudProperties {

    /** Liga/desliga a integração inteira (feature flag). Default: desligada. */
    private boolean enabled = false;

    /** Verify token do handshake GET (comparado em tempo constante). */
    private String verifyToken;

    /** App Secret da aplicação Meta (chave do HMAC-SHA256 da assinatura). */
    private String appSecret;

    /** Access token (Bearer) para chamar a Graph API. */
    private String accessToken;

    /** Phone Number ID (rota outbound {version}/{phone-number-id}/messages). */
    private String phoneNumberId;

    /** WhatsApp Business Account ID. */
    private String wabaId;

    /** Versão da Graph API — SEM default; configuração explícita obrigatória. */
    private String graphApiVersion;

    /** Base URL da Graph API. Default oficial público, sobrescrevível em teste. */
    private String baseUrl = "https://graph.facebook.com";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getWabaId() {
        return wabaId;
    }

    public void setWabaId(String wabaId) {
        this.wabaId = wabaId;
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
