package com.troquim_bot.whatsapp.flow.support;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * Lado CLIENTE do protocolo criptográfico dos Flows, para os testes.
 *
 * Implementa o papel da Meta: gera a chave AES efêmera, cifra com a chave pública,
 * cifra o corpo com AES-GCM e decifra a resposta com o IV invertido. Escrito de forma
 * independente do {@code FlowCipher} de produção — se ambos concordarem, a
 * interoperabilidade com a Meta está de fato exercitada, e não apenas o código chamando
 * a si mesmo.
 *
 * O par de chaves é GERADO em memória a cada execução: nenhuma chave de teste é
 * versionada no repositório.
 */
public final class FlowTestCrypto {

    private final KeyPair keyPair;

    public FlowTestCrypto() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar par de chaves de teste", e);
        }
    }

    /** Chave privada em PEM PKCS#8, no formato aceito pela configuração. */
    public String privateKeyPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    /** Sessão criptográfica de uma requisição: chave AES + IV, reusados na resposta. */
    public Sessao novaSessao() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey aesKey = generator.generateKey();

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            return new Sessao(aesKey.getEncoded(), iv);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar sessão AES de teste", e);
        }
    }

    /** Cifra a chave AES com a chave PÚBLICA, como a Meta faz. */
    public String cifrarChaveAes(byte[] aesKey) {
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(),
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            return Base64.getEncoder().encodeToString(rsa.doFinal(aesKey));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar chave AES de teste", e);
        }
    }

    public String cifrarCorpo(String json, Sessao sessao) {
        try {
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessao.aesKey(), "AES"),
                    new GCMParameterSpec(128, sessao.iv()));
            return Base64.getEncoder()
                    .encodeToString(aes.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar corpo de teste", e);
        }
    }

    /** Decifra a resposta usando o IV INVERTIDO, como o cliente da Meta faz. */
    public String decifrarResposta(String base64, Sessao sessao) {
        try {
            byte[] ivInvertido = new byte[sessao.iv().length];
            for (int i = 0; i < ivInvertido.length; i++) {
                ivInvertido[i] = (byte) ~sessao.iv()[i];
            }

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessao.aesKey(), "AES"),
                    new GCMParameterSpec(128, ivInvertido));
            return new String(aes.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar resposta de teste", e);
        }
    }

    /** Envelope JSON pronto para POST. */
    public String envelope(String json, Sessao sessao) {
        return """
                {"encrypted_flow_data":"%s","encrypted_aes_key":"%s","initial_vector":"%s"}"""
                .formatted(cifrarCorpo(json, sessao),
                        cifrarChaveAes(sessao.aesKey()),
                        Base64.getEncoder().encodeToString(sessao.iv()));
    }

    public record Sessao(byte[] aesKey, byte[] iv) {
    }
}
