package com.troquim_bot.controller.dto;

/**
 * Forma ÚNICA de erro da API pública de agendamento: {@code code} estável (para o cliente
 * programar contra) + {@code message} humana. Nunca carrega stack trace, exceção interna,
 * ou qualquer detalhe que distinga motivos que devem parecer iguais de fora (ex.: serviço
 * inexistente vs. de outro tenant).
 */
public class PublicErrorResponse {

    private String code;
    private String message;

    public PublicErrorResponse() {
    }

    public PublicErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
