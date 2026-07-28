package com.troquim_bot.whatsapp.channel.infrastructure.crypto;

import com.troquim_bot.whatsapp.channel.application.ChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.application.EncryptedCredential;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifragem da credencial do canal em AES-256-GCM.
 *
 * GCM porque é autenticado: além de esconder o token, detecta adulteração do texto
 * cifrado — um byte trocado no banco faz a decifragem falhar em vez de devolver lixo
 * silenciosamente.
 *
 * IV de 12 bytes, novo a cada cifragem e guardado ao lado do texto. Reusar IV com a
 * mesma chave em GCM é uma falha catastrófica (revela relação entre mensagens), por
 * isso ele nunca é derivado nem fixo.
 *
 * A chave vem de {@link ChannelCryptoProperties} — variável de ambiente, nunca do
 * banco nem do repositório. {@code keyVersion} é gravado junto para que uma rotação
 * futura possa decifrar linhas antigas sem reescrever a tabela.
 */
@Component
public class AesGcmChannelCredentialCipher implements ChannelCredentialCipher {

    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final ChannelCryptoProperties properties;
    private final SecureRandom random = new SecureRandom();

    /**
     * A chave é resolvida na PRIMEIRA cifragem, não no construtor.
     *
     * O onboarding é opcional e vem desligado: validar a chave no boot faria toda
     * instância que não usa a feature deixar de subir. Assim, quem não conecta canal
     * nunca precisa da chave, e quem tenta conectar sem ela recebe uma falha explícita
     * em vez de gravar credencial fraca.
     */
    private volatile SecretKeySpec chave;

    public AesGcmChannelCredentialCipher(ChannelCryptoProperties properties) {
        this.properties = properties;
    }

    private SecretKeySpec chave() {
        SecretKeySpec atual = chave;
        if (atual == null) {
            synchronized (this) {
                atual = chave;
                if (atual == null) {
                    atual = new SecretKeySpec(properties.chaveDecodificada(), "AES");
                    chave = atual;
                }
            }
        }
        return atual;
    }

    @Override
    public EncryptedCredential cifrar(String textoClaro) {
        if (textoClaro == null || textoClaro.isBlank()) {
            throw new IllegalArgumentException("Nao ha credencial para cifrar");
        }
        // Fora do try: chave ausente/malformada é erro de CONFIGURAÇÃO e precisa subir
        // com a mensagem que diz qual variável definir, não virar um "IllegalStateException"
        // genérico junto com as falhas de criptografia.
        SecretKeySpec chave = chave();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));

            return new EncryptedCredential(
                    Base64.getEncoder().encodeToString(cifrado),
                    Base64.getEncoder().encodeToString(iv),
                    properties.getKeyVersion());
        } catch (Exception e) {
            // Nem a exceção original nem o texto claro sobem: só o tipo.
            throw new IllegalStateException(
                    "Falha ao cifrar credencial do canal: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public String decifrar(EncryptedCredential credencial) {
        if (credencial.keyVersion() != properties.getKeyVersion()) {
            throw new IllegalStateException(
                    "Credencial cifrada com a chave versao " + credencial.keyVersion()
                            + ", indisponivel nesta instancia (atual: " + properties.getKeyVersion() + ")");
        }
        SecretKeySpec chave = chave();
        try {
            byte[] iv = Base64.getDecoder().decode(credencial.ivBase64());
            byte[] cifrado = Base64.getDecoder().decode(credencial.cipherTextBase64());

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha ao decifrar credencial do canal: " + e.getClass().getSimpleName());
        }
    }
}
