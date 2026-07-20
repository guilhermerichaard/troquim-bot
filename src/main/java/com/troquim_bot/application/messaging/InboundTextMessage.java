package com.troquim_bot.application.messaging;

/**
 * Mensagem de texto recebida, em contrato INTERNO provider-neutral.
 *
 * Nenhum tipo específico da Meta (WhatsApp Cloud) chega até aqui: o parser de
 * infraestrutura converte o payload externo nestes campos neutros antes de
 * qualquer orquestração. {@code provider} identifica o canal de origem (ex:
 * "whatsapp_cloud") e alimenta a idempotência durável por (provider, id externo).
 *
 * @param provider          canal de origem (provider-neutral key)
 * @param externalMessageId id da mensagem no provedor externo (idempotência)
 * @param fromPhone         telefone do remetente já normalizado pela camada de integração
 * @param text              corpo textual da mensagem
 * @param timestampEpoch    epoch (segundos) informado pelo provedor, ou 0 se ausente
 */
public record InboundTextMessage(
        String provider,
        String externalMessageId,
        String fromPhone,
        String text,
        long timestampEpoch) {
}
