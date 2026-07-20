package com.troquim_bot.application.messaging;

/**
 * Porta da verificação de assinatura do webhook (handshake GET do provedor).
 * A implementação compara, em tempo constante, o verify token recebido contra o
 * configurado. O verify token nunca é registrado em log.
 */
public interface SubscriptionVerifier {

    /** @return true se {@code providedToken} corresponder ao verify token configurado. */
    boolean matches(String providedToken);
}
