package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.WebhookSignatureVerifier;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida {@code X-Hub-Signature-256} da Meta: {@code sha256=<hex>}, onde o hex é o
 * HMAC-SHA256 dos BYTES EXATOS do corpo, com chave = App Secret. Comparação em tempo
 * constante. Nunca registra App Secret, assinatura ou corpo.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudSignatureVerifier implements WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WhatsAppCloudProperties properties;

    public WhatsAppCloudSignatureVerifier(WhatsAppCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String appSecret = properties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            return false;
        }

        String providedHex = signatureHeader.substring(PREFIX.length());
        byte[] provided = hexToBytes(providedHex);
        if (provided == null) {
            return false;
        }

        byte[] expected = hmacSha256(appSecret, rawBody);
        // MessageDigest.isEqual é comparação em tempo constante.
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular HMAC-SHA256", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() % 2) != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
