package com.troquim_bot.application.messaging;

/**
 * Desfecho do processamento idempotente de UMA mensagem inbound dentro do limite
 * transacional do receipt. {@code processed=false} indica duplicata já registrada
 * (não chamar Conversation nem outbound novamente).
 */
public record ProcessOutcome(boolean processed, String fromPhone, String responseText) {

    public static ProcessOutcome duplicate() {
        return new ProcessOutcome(false, null, null);
    }

    public static ProcessOutcome processed(String fromPhone, String responseText) {
        return new ProcessOutcome(true, fromPhone, responseText);
    }
}
