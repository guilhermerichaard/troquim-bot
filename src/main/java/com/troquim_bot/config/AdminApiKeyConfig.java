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

        // Surrounding whitespace (typically a trailing newline injected with the
        // container secret) never matches a client-sent token: BearerTokenFilter
        // compares the configured key against the trimmed incoming token, so a
        // padded key silently rejects every valid request with 401. Fail loudly at
        // startup instead of trimming silently — a malformed secret must be fixed,
        // not quietly accepted.
        if (!adminApiKey.equals(adminApiKey.strip())) {
            throw new IllegalStateException(
                "TROQUIM_ADMIN_API_KEY has leading or trailing whitespace and would never match a " +
                "client token (every authenticated request would return 401). Provide the key without " +
                "surrounding whitespace — check for a trailing newline in the container secret."
            );
        }
    }
}
