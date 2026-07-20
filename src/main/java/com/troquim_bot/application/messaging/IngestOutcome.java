package com.troquim_bot.application.messaging;

/**
 * Desfecho neutro do processamento de um POST de webhook, mapeado para HTTP pela
 * camada de interface (controller):
 *
 * <ul>
 *   <li>{@link #SIGNATURE_INVALID} → 401 (assinatura ausente/inválida)</li>
 *   <li>{@link #MALFORMED} → 400 (corpo não desserializável)</li>
 *   <li>{@link #ACCEPTED} → 200 (processado, duplicado, status-only ou sem mensagem)</li>
 *   <li>{@link #OUTBOUND_UNAVAILABLE} → 503 (negócio processado e persistido, mas o envio
 *       da resposta falhou; um NÃO-2xx faz a Meta reentregar o evento, disparando o retry
 *       do outbound sem reprocessar a conversa)</li>
 * </ul>
 */
public enum IngestOutcome {
    SIGNATURE_INVALID,
    MALFORMED,
    ACCEPTED,
    OUTBOUND_UNAVAILABLE
}
