package com.troquim_bot.application.messaging;

/**
 * Porta de validação criptográfica do webhook. A implementação (infraestrutura)
 * conhece o algoritmo e o segredo do provedor (HMAC-SHA256 com App Secret da Meta).
 * A comparação é feita sobre os BYTES BRUTOS do corpo, antes de qualquer parsing.
 */
public interface WebhookSignatureVerifier {

    /**
     * @param rawBody         bytes exatos do corpo do request
     * @param signatureHeader valor do header de assinatura (pode ser null/ausente)
     * @return true se a assinatura corresponder ao corpo; false se ausente/inválida
     */
    boolean isValid(byte[] rawBody, String signatureHeader);
}
