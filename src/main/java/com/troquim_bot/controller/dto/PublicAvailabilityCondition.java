package com.troquim_bot.controller.dto;

import com.troquim_bot.application.availability.ConsultarDisponibilidade;

/**
 * Tradução da {@link ConsultarDisponibilidade.Condicao} interna para um código público
 * ESTÁVEL. Nunca serializa o nome do enum interno diretamente: renomear ou reordenar
 * {@code Condicao} não pode mudar silenciosamente o contrato de quem consome a API pública.
 */
public final class PublicAvailabilityCondition {

    private PublicAvailabilityCondition() {
    }

    public static String de(ConsultarDisponibilidade.Condicao condicao) {
        return switch (condicao) {
            case DISPONIVEL -> "AVAILABLE";
            case EXPEDIENTE_NAO_CONFIGURADO -> "BUSINESS_HOURS_NOT_CONFIGURED";
            case DIA_FECHADO -> "BUSINESS_CLOSED";
            case PROFISSIONAL_SEM_DISPONIBILIDADE -> "PROFESSIONAL_UNAVAILABLE";
            case SERVICO_INDISPONIVEL -> "SERVICE_UNAVAILABLE";
            case PROFISSIONAL_INDISPONIVEL -> "PROFESSIONAL_UNAVAILABLE";
            case DATA_PASSADA -> "PAST_DATE";
            case AGENDA_CHEIA -> "FULLY_BOOKED";
        };
    }
}
