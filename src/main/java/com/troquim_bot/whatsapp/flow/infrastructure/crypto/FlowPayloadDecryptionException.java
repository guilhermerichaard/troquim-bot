package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

/**
 * Falha ao decifrar/autenticar o corpo AES-128-GCM, ou envelope malformado.
 *
 * Diferente de {@link FlowKeyDecryptionException}: aqui a chave RSA funcionou, logo
 * não há dessincronia de chaves — o request é simplesmente inválido (base64 corrompido,
 * IV de tamanho errado, tag GCM que não confere). Mapeia para HTTP 400/500, nunca 421.
 *
 * A mensagem NUNCA contém material criptográfico nem payload.
 */
public class FlowPayloadDecryptionException extends RuntimeException {

    public FlowPayloadDecryptionException(String message) {
        super(message);
    }

    public FlowPayloadDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
