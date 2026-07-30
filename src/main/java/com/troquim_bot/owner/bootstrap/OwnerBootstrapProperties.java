package com.troquim_bot.owner.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração tipada do provisionamento do primeiro owner do /app. Prefixo
 * {@code troquim.owner.bootstrap}.
 *
 * DESLIGADO por padrão ({@code enabled=false}): o provisionamento só age quando ligado
 * explicitamente no deploy. E-mail e senha vêm de variável de ambiente
 * (TROQUIM_OWNER_BOOTSTRAP_EMAIL / TROQUIM_OWNER_BOOTSTRAP_PASSWORD); a senha em claro
 * nunca é logada nem persistida — só serve de entrada para o hash BCrypt.
 *
 * A senha vive aqui apenas durante o startup. O Actuator deste projeto expõe só o
 * health ({@code management.endpoints.web.exposure.include=health}), então nem
 * {@code /env} nem {@code /configprops} vazam este valor. Por isso não há toString
 * que imprima a senha.
 */
@ConfigurationProperties(prefix = "troquim.owner.bootstrap")
public class OwnerBootstrapProperties {

    private boolean enabled = false;
    private String email;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
