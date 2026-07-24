package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Carrega a chave privada RSA do Flow a partir da configuração externa.
 *
 * A chave é lida UMA vez na construção do bean (fail-fast: a aplicação não sobe com
 * a integração ligada e chave ausente/ilegível) e mantida apenas em memória. Nem a
 * chave, nem a senha, nem qualquer fragmento delas aparecem em mensagem de exceção
 * ou em log.
 *
 * Aceita PEM PKCS#8 em duas formas:
 * <ul>
 *   <li>{@code -----BEGIN PRIVATE KEY-----} (sem senha)</li>
 *   <li>{@code -----BEGIN ENCRYPTED PRIVATE KEY-----} (com senha em
 *       {@code WHATSAPP_FLOW_PRIVATE_KEY_PASSWORD})</li>
 * </ul>
 * Quebras de linha podem vir reais ou escapadas como {@code \n}, para acomodar o
 * transporte por variável de ambiente.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowPrivateKeyProvider {

    private static final String HEADER_PLAIN = "-----BEGIN PRIVATE KEY-----";
    private static final String FOOTER_PLAIN = "-----END PRIVATE KEY-----";
    private static final String HEADER_ENCRYPTED = "-----BEGIN ENCRYPTED PRIVATE KEY-----";
    private static final String FOOTER_ENCRYPTED = "-----END ENCRYPTED PRIVATE KEY-----";

    private final PrivateKey privateKey;

    public FlowPrivateKeyProvider(WhatsAppFlowProperties properties) {
        this.privateKey = carregar(properties.getPrivateKey(), properties.getPrivateKeyPassword());
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    private static PrivateKey carregar(String pem, String senha) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "troquim.integrations.whatsapp.flow.private-key é obrigatório quando o Flow está ligado "
                            + "(configure WHATSAPP_FLOW_PRIVATE_KEY)");
        }

        String normalizado = pem.replace("\\n", "\n").trim();
        boolean cifrada = normalizado.contains(HEADER_ENCRYPTED);

        byte[] der = decodificarCorpo(normalizado, cifrada);
        try {
            PKCS8EncodedKeySpec spec = cifrada
                    ? specDeChaveCifrada(der, senha)
                    : new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Sem cause encadeada com conteúdo da chave: apenas o tipo da falha.
            throw new IllegalStateException(
                    "Chave privada do WhatsApp Flow inválida ou senha incorreta ("
                            + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] decodificarCorpo(String pem, boolean cifrada) {
        String header = cifrada ? HEADER_ENCRYPTED : HEADER_PLAIN;
        String footer = cifrada ? FOOTER_ENCRYPTED : FOOTER_PLAIN;

        int inicio = pem.indexOf(header);
        int fim = pem.indexOf(footer);
        if (inicio < 0 || fim < 0) {
            throw new IllegalStateException(
                    "Chave privada do WhatsApp Flow não está em formato PEM PKCS#8 reconhecido");
        }

        String base64 = pem.substring(inicio + header.length(), fim).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Chave privada do WhatsApp Flow não é base64 válido");
        }
    }

    private static PKCS8EncodedKeySpec specDeChaveCifrada(byte[] der, String senha) throws Exception {
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException(
                    "A chave privada do WhatsApp Flow está cifrada mas nenhuma senha foi configurada "
                            + "(configure WHATSAPP_FLOW_PRIVATE_KEY_PASSWORD)");
        }

        EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(der);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(info.getAlgName());
        SecretKey chaveDaSenha = factory.generateSecret(new PBEKeySpec(senha.toCharArray()));

        Cipher cipher = Cipher.getInstance(info.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, chaveDaSenha, info.getAlgParameters());
        return info.getKeySpec(cipher);
    }
}
