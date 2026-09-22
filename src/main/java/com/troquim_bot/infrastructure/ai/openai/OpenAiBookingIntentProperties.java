package com.troquim_bot.infrastructure.ai.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração do experimento de interpretação de intenção por IA.
 *
 * Desligado por padrão. A API key nunca é versionada e vem exclusivamente do ambiente.
 */
@Component
@ConfigurationProperties(prefix = "troquim.ai.booking-intent")
public class OpenAiBookingIntentProperties {

    private boolean enabled;
    private String apiKey = "";
    private String model = "gpt-5.6-luna";
    private String baseUrl = "https://api.openai.com/v1";
    private int timeoutMs = 1800;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
