package com.troquim_bot.whatsapp.channel.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

/**
 * Chave de cifragem das credenciais de canal. Prefixo
 * {@code troquim.integrations.whatsapp.channel.crypto}.
 *
 * A chave vem SEMPRE de variável de ambiente e não tem default: uma chave padrão em
 * código valeria o mesmo que não cifrar. Sem ela configurada, a aplicação falha ao
 * subir com a integração ligada, em vez de gravar credencial fraca em silêncio.
 */
@ConfigurationProperties(prefix = "troquim.integrations.whatsapp.channel.crypto")
public class ChannelCryptoProperties {

    private static final int AES_256_BYTES = 32;

    /** Chave AES-256 em base64 (32 bytes). Origem: variável de ambiente. */
    private String key;

    /** Versão da chave corrente, gravada em cada credencial para permitir rotação. */
    private int keyVersion = 1;

    /**
     * Decodifica e valida o tamanho. Falha explícita é melhor que uma chave curta
     * silenciosamente aceita — o {@code SecretKeySpec} aceitaria 16 bytes e nós
     * acharíamos que estávamos em AES-256.
     */
    public byte[] chaveDecodificada() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "troquim.integrations.whatsapp.channel.crypto.key e obrigatoria "
                            + "(defina TROQUIM_CHANNEL_CRYPTO_KEY com 32 bytes em base64)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "troquim.integrations.whatsapp.channel.crypto.key nao e base64 valido");
        }
        if (bytes.length != AES_256_BYTES) {
            throw new IllegalStateException(
                    "A chave de cifragem precisa ter 32 bytes (AES-256); recebeu "
                            + bytes.length);
        }
        return bytes;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(int keyVersion) {
        this.keyVersion = keyVersion;
    }
}
