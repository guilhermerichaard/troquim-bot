package com.troquim_bot.application.messaging;

/**
 * Conclusao de uma experiencia interativa (ex.: WhatsApp Flow).
 *
 * O payload externo pode trazer varios campos de apresentacao, mas a Application aceita
 * somente a identidade minima necessaria: provider/id para idempotencia, telefone para
 * resposta e flowToken para correlacionar com a sessao emitida pelo proprio Troquim.
 *
 * Nenhum dado de servico/data/horario recebido do cliente e tratado como verdade.
 */
public record InboundFlowCompletion(
        String provider,
        String externalMessageId,
        String fromPhone,
        String flowToken,
        long timestampEpoch) {
}
