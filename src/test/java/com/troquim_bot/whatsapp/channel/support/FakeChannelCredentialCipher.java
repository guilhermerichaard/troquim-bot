package com.troquim_bot.whatsapp.channel.support;

import com.troquim_bot.whatsapp.channel.application.ChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.application.EncryptedCredential;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cifra falsa (base64 reversível) para os testes que exercitam ORQUESTRAÇÃO, não
 * criptografia. A cifragem real tem teste próprio em
 * {@code AesGcmChannelCredentialCipherTest}.
 *
 * Deliberadamente não é AES: se um teste de fluxo passasse a depender do formato do
 * texto cifrado, ele quebraria numa rotação de chave — e o que ele deveria provar é
 * outra coisa.
 */
public class FakeChannelCredentialCipher implements ChannelCredentialCipher {

    private static final int KEY_VERSION = 1;

    @Override
    public EncryptedCredential cifrar(String textoClaro) {
        return new EncryptedCredential(
                Base64.getEncoder().encodeToString(textoClaro.getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString("iv-de-teste-".getBytes(StandardCharsets.UTF_8)),
                KEY_VERSION);
    }

    @Override
    public String decifrar(EncryptedCredential credencial) {
        return new String(Base64.getDecoder().decode(credencial.cipherTextBase64()),
                StandardCharsets.UTF_8);
    }
}
