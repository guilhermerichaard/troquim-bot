package com.troquim_bot.owner.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "troquim.owner.otp")
public class OwnerOtpProperties {
    private boolean enabled = false;
    private String pepper;
    private String templateName;
    private String templateLanguage = "pt_BR";
    private int ttlMinutes = 5;
    private int maxAttempts = 5;
    private int maxRequestsPer10Minutes = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPepper() { return pepper; }
    public void setPepper(String pepper) { this.pepper = pepper; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getTemplateLanguage() { return templateLanguage; }
    public void setTemplateLanguage(String templateLanguage) { this.templateLanguage = templateLanguage; }
    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getMaxRequestsPer10Minutes() { return maxRequestsPer10Minutes; }
    public void setMaxRequestsPer10Minutes(int maxRequestsPer10Minutes) { this.maxRequestsPer10Minutes = maxRequestsPer10Minutes; }

    public boolean configured() {
        return enabled && pepper != null && pepper.length() >= 32
                && templateName != null && !templateName.isBlank()
                && ttlMinutes > 0 && maxAttempts > 0 && maxRequestsPer10Minutes > 0;
    }
}
