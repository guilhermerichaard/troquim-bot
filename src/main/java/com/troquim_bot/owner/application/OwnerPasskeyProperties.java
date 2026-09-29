package com.troquim_bot.owner.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "troquim.owner.passkey")
public class OwnerPasskeyProperties {
    private boolean enabled = false;
    private String rpId = "app.troquim.app";
    private String rpName = "Troquim";
    private String allowedOrigins = "https://app.troquim.app";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRpId() { return rpId; }
    public void setRpId(String rpId) { this.rpId = rpId; }
    public String getRpName() { return rpName; }
    public void setRpName(String rpName) { this.rpName = rpName; }
    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public Set<String> origins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    public boolean configured() {
        return enabled && rpId != null && !rpId.isBlank() && rpName != null && !rpName.isBlank()
                && !origins().isEmpty();
    }
}
