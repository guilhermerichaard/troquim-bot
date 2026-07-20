package com.troquim_bot.application.messaging;

/**
 * Sinaliza que o {@code claimPending} de um receipt colidiu com a UNIQUE(provider,
 * external_message_id) — isto é, uma entrega CONCORRENTE com a mesma message ID.
 *
 * Existe para DISTINGUIR o conflito do claim (que é uma duplicata → 200) de qualquer
 * outra {@code DataIntegrityViolationException} vinda do domínio (constraint de negócio),
 * que NÃO deve ser engolida: deve propagar para a Meta reentregar e reprocessar.
 */
public class ConcurrentReceiptClaimException extends RuntimeException {

    public ConcurrentReceiptClaimException(String provider, String externalMessageId, Throwable cause) {
        super("Claim concorrente de receipt (provider=" + provider + ", id=" + externalMessageId + ")", cause);
    }
}
