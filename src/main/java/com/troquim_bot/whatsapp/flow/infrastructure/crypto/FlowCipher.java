package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Implementação do protocolo criptográfico oficial dos WhatsApp Flows (Meta).
 *
 * Camada de INFRAESTRUTURA: só transforma bytes. Não conhece telas, agendamento nem
 * qualquer regra de negócio, e nunca registra em log o texto claro, a chave AES ou o IV.
 *
 * Fluxo por requisição:
 * <ol>
 *   <li>{@code encrypted_aes_key} é decifrado com RSA-OAEP (SHA-256, MGF1 com SHA-256)
 *       usando a chave privada local, produzindo uma chave AES-128 efêmera;</li>
 *   <li>{@code encrypted_flow_data} é decifrado com AES-128-GCM usando essa chave e o
 *       {@code initial_vector} recebido — os 16 bytes finais são a tag de autenticação,
 *       tratada nativamente pelo provider Java;</li>
 *   <li>a resposta é cifrada com a MESMA chave AES e o IV INVERTIDO bit a bit, e devolvida
 *       como base64 de {@code ciphertext || tag}.</li>
 * </ol>
 *
 * A inversão do IV é exigência do protocolo: reutilizar o mesmo IV com a mesma chave
 * quebraria a segurança do GCM.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowCipher {

    private static final String RSA_TRANSFORMACAO = "RSA/ECB/OAEPPadding";
    private static final String AES_TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 16;
    private static final int AES_KEY_BYTES = 16;

    private final FlowPrivateKeyProvider keyProvider;

    public FlowCipher(FlowPrivateKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Decifra a chave AES efêmera. Qualquer falha aqui é dessincronia de chaves e
     * DEVE virar HTTP 421 na borda.
     */
    public byte[] decifrarChaveAes(String encryptedAesKeyBase64) {
        byte[] cifrada;
        try {
            cifrada = Base64.getDecoder().decode(exigirTexto(encryptedAesKeyBase64, "encrypted_aes_key"));
        } catch (IllegalArgumentException e) {
            throw new FlowKeyDecryptionException("encrypted_aes_key não é base64 válido", e);
        }

        try {
            Cipher rsa = Cipher.getInstance(RSA_TRANSFORMACAO);
            // MGF1 explicitamente com SHA-256: o default de alguns providers é SHA-1,
            // o que produziria falha silenciosa de interoperabilidade com a Meta.
            rsa.init(Cipher.DECRYPT_MODE, keyProvider.privateKey(),
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            byte[] aesKey = rsa.doFinal(cifrada);
            if (aesKey.length != AES_KEY_BYTES) {
                throw new FlowKeyDecryptionException(
                        "Chave AES com tamanho inesperado: " + aesKey.length + " bytes", null);
            }
            return aesKey;
        } catch (FlowKeyDecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowKeyDecryptionException(
                    "Falha ao decifrar a chave AES (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** Decodifica o IV recebido. Tamanho inválido é request malformado, não erro de chave. */
    public byte[] decodificarIv(String initialVectorBase64) {
        byte[] iv;
        try {
            iv = Base64.getDecoder().decode(exigirTexto(initialVectorBase64, "initial_vector"));
        } catch (IllegalArgumentException e) {
            throw new FlowPayloadDecryptionException("initial_vector não é base64 válido", e);
        }
        if (iv.length != IV_BYTES) {
            throw new FlowPayloadDecryptionException(
                    "initial_vector com tamanho inesperado: " + iv.length + " bytes");
        }
        return iv;
    }

    /** Decifra e autentica o corpo do request. Retorna o JSON em texto claro. */
    public String decifrarCorpo(String encryptedFlowDataBase64, byte[] aesKey, byte[] iv) {
        byte[] cifrado;
        try {
            cifrado = Base64.getDecoder().decode(
                    exigirTexto(encryptedFlowDataBase64, "encrypted_flow_data"));
        } catch (IllegalArgumentException e) {
            throw new FlowPayloadDecryptionException("encrypted_flow_data não é base64 válido", e);
        }

        try {
            Cipher aes = Cipher.getInstance(AES_TRANSFORMACAO);
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(aes.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new FlowPayloadDecryptionException(
                    "Falha ao decifrar/autenticar o corpo do Flow (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /**
     * Cifra a resposta com a mesma chave AES e o IV invertido bit a bit, conforme o
     * protocolo. Saída: base64 de {@code ciphertext || tag}.
     */
    public String cifrarResposta(String json, byte[] aesKey, byte[] iv) {
        byte[] ivInvertido = new byte[iv.length];
        for (int i = 0; i < iv.length; i++) {
            ivInvertido[i] = (byte) ~iv[i];
        }

        try {
            Cipher aes = Cipher.getInstance(AES_TRANSFORMACAO);
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, ivInvertido));
            return Base64.getEncoder().encodeToString(aes.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new FlowPayloadDecryptionException(
                    "Falha ao cifrar a resposta do Flow (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new FlowPayloadDecryptionException("Campo obrigatório ausente no envelope: " + campo);
        }
        return valor;
    }
}
