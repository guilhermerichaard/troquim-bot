package com.troquim_bot.application.messaging;

/**
 * Resultado neutro do envio de uma mensagem outbound.
 *
 * @param externalMessageId id atribuído pelo provedor à mensagem enviada (pode ser null)
 * @param status            status textual sanitizado (ex: "sent")
 */
public record OutboundResult(String externalMessageId, String status) {
}
