package com.troquim_bot.whatsapp.channel.application;

/**
 * Porta de cifragem da credencial do canal. A Application define o contrato; a
 * Infrastructure escolhe o algoritmo e de onde vem a chave.
 *
 * Existe para que o access token nunca chegue ao banco em claro. Um dump, um backup
 * ou um SELECT distraído não podem render uma credencial utilizável.
 */
public interface ChannelCredentialCipher {

    /** Cifra o token. Cada chamada usa um IV novo. */
    EncryptedCredential cifrar(String textoClaro);

    /**
     * Decifra. Chamada apenas no momento de usar a credencial — nunca para exibir,
     * listar ou responder a uma requisição.
     *
     * @throws IllegalStateException se a versão de chave gravada não estiver disponível
     */
    String decifrar(EncryptedCredential credencial);
}
