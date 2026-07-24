package com.troquim_bot.whatsapp.flow.application;

import java.util.Locale;
import java.util.Optional;

/**
 * Eventos CANÔNICOS de negócio do Flow de agendamento.
 *
 * O protocolo da Meta só distingue {@code ping}, {@code INIT}, {@code BACK} e
 * {@code data_exchange}. Qual passo do agendamento está acontecendo vem no campo
 * estável {@code flow_action} dentro de {@code data} — declarado nos payloads do
 * Flow JSON e, portanto, contrato explícito e versionável.
 *
 * Estes seis nomes são o contrato único entre o Flow JSON e o backend. Renomear
 * qualquer um deles quebra Flows já publicados.
 *
 * Entrada do cliente é NÃO CONFIÁVEL: valor desconhecido nunca vira exceção de
 * runtime — vira {@link Optional#empty()} e resposta de erro controlada.
 */
public enum FlowAction {

    /** Serviço escolhido no dropdown (on-select): re-renderiza SERVICO com profissionais. */
    SERVICO_SELECIONADO,

    /** Footer de SERVICO: valida serviço/profissional e navega para AGENDA com as datas. */
    BUSCAR_DATAS,

    /** Data escolhida no dropdown (on-select): re-renderiza AGENDA com os horários do dia. */
    DATA_SELECIONADA,

    /** Footer de AGENDA: valida data+horário e navega para CLIENTE. */
    HORARIO_SELECIONADO,

    /** Footer de CLIENTE: monta o resumo e navega para CONFIRMACAO. */
    MONTAR_RESUMO,

    /** Footer de CONFIRMACAO: confirma no domínio e encerra com a tela reservada SUCCESS. */
    CONFIRMAR_AGENDAMENTO;

    public static Optional<FlowAction> parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
