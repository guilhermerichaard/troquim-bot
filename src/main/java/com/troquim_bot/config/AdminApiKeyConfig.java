package com.troquim_bot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Configures and validates the administrative API key. */
@Configuration
public class AdminApiKeyConfig {

    static final String PLACEHOLDER = "change-me";

    private final String adminApiKey;
    private final Environment environment;

    public AdminApiKeyConfig(@Value("${troquim.admin.api-key:}") String adminApiKey,
                             Environment environment) {
        this.adminApiKey = adminApiKey;
        this.environment = environment;
    }

    @Bean
    public String adminApiKey() {
        return adminApiKey;
    }

    /** Fails startup when the Azure profile has no real key. */
    @PostConstruct
    void validateAdminApiKeyInAzure() {
        if (!environment.acceptsProfiles(Profiles.of("azure"))) {
            return;
        }

        if (adminApiKey == null || adminApiKey.isBlank() || PLACEHOLDER.equals(adminApiKey)) {
            throw new IllegalStateException(
                "TROQUIM_ADMIN_API_KEY must be configured for the azure profile. " +
                "Set a valid API key via environment variable before starting."
            );
        }
    }
}
