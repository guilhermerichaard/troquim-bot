package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.SubscriptionVerifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Compara, em tempo constante, o verify token recebido no handshake GET contra o
 * configurado. Nunca registra o verify token.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudSubscriptionVerifier implements SubscriptionVerifier {

    private final WhatsAppCloudProperties properties;

    public WhatsAppCloudSubscriptionVerifier(WhatsAppCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean matches(String providedToken) {
        String configured = properties.getVerifyToken();
        if (configured == null || configured.isBlank() || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
