package com.troquim_bot.application.messaging;

/**
 * Porta provider-neutral de parsing. A implementação (infraestrutura) conhece o
 * formato do provedor (Meta/WhatsApp Cloud) e o converte no contrato interno.
 * Os tipos/DTOs específicos da Meta terminam na implementação; nada vaza para cá.
 */
public interface InboundMessageParser {

    /**
     * Converte o corpo bruto do webhook em mensagens neutras.
     *
     * @param rawBody bytes exatos do corpo do request (já com assinatura validada)
     * @return payload neutro; nunca null
     * @throws InboundPayloadFormatException se o corpo não for desserializável
     */
    ParsedInboundPayload parse(byte[] rawBody);

    /** Falha de formato do payload (corpo malformado) — mapeada para 400. */
    class InboundPayloadFormatException extends RuntimeException {
        public InboundPayloadFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
