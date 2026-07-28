package com.troquim_bot.whatsapp.channel.application;

/**
 * Credencial já cifrada, pronta para persistir.
 *
 * Os três campos andam juntos porque decifrar exige os três: o texto cifrado, o IV
 * daquela escrita (novo a cada cifragem — reusar IV em GCM quebra a confidencialidade)
 * e a versão da chave usada, para que uma rotação não invalide o que já está gravado.
 *
 * Não existe construtor a partir de texto claro aqui: quem cifra é
 * {@link ChannelCredentialCipher}, e o claro nunca transita por este tipo.
 */
public record EncryptedCredential(String cipherTextBase64, String ivBase64, int keyVersion) {

    public EncryptedCredential {
        if (cipherTextBase64 == null || cipherTextBase64.isBlank()) {
            throw new IllegalArgumentException("cipherText é obrigatório");
        }
        if (ivBase64 == null || ivBase64.isBlank()) {
            throw new IllegalArgumentException("IV é obrigatório");
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion deve ser positiva");
        }
    }

    /** Nunca revela o material cifrado — só o suficiente para diagnosticar. */
    @Override
    public String toString() {
        return "EncryptedCredential[keyVersion=" + keyVersion + ", bytes=<ocultos>]";
    }
}
