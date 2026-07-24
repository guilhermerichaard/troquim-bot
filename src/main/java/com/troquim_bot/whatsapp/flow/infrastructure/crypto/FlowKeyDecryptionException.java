package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

/**
 * Falha ao decifrar a chave AES efêmera com a chave privada RSA.
 *
 * Sinaliza dessincronia entre a chave pública registrada na Meta e a chave privada
 * local. O protocolo exige responder HTTP 421 nesse caso — a Meta então refaz o
 * handshake de chave pública e reenvia a requisição.
 *
 * A mensagem NUNCA contém material criptográfico nem payload.
 */
public class FlowKeyDecryptionException extends RuntimeException {

    public FlowKeyDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
