package com.troquim_bot.whatsapp.channel.application;

/**
 * Porta da troca do OAuth code por access token na Meta.
 *
 * Existe para manter o App Secret confinado à Infrastructure: a Application pede "troque
 * este code", nunca monta a chamada nem conhece o segredo. É também o ponto de corte que
 * permite testar o fluxo inteiro sem tocar a Graph API.
 */
public interface MetaOAuthGateway {

    /**
     * Troca o {@code code} do Embedded Signup por um access token.
     *
     * @return o token em claro — o chamador deve cifrá-lo imediatamente e não retê-lo
     * @throws MetaOAuthException se a Meta recusar o code ou a resposta vier sem token
     */
    String trocarCodePorToken(String code);

    /** Falha na troca. A mensagem é curta e estável; nunca ecoa a resposta da Meta. */
    class MetaOAuthException extends RuntimeException {
        public MetaOAuthException(String message) {
            super(message);
        }

        public MetaOAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
