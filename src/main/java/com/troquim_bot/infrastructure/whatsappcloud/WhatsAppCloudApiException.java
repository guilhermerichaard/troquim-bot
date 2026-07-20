package com.troquim_bot.infrastructure.whatsappcloud;

/**
 * Erro tipado de infraestrutura ao falar com a Graph API (HTTP 4xx/5xx, timeout ou
 * transporte). A mensagem é sanitizada: nunca inclui Authorization nem o corpo
 * pessoal — apenas status e um descritor curto.
 */
public class WhatsAppCloudApiException extends RuntimeException {

    private final Integer statusCode;

    public WhatsAppCloudApiException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
