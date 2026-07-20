package com.troquim_bot.application.messaging;

import java.util.Optional;

/**
 * Porta de persistência durável do estado de processamento de eventos externos
 * (idempotência). A implementação (infraestrutura) usa uma tabela de integração
 * com UNIQUE(provider, external_message_id). É responsabilidade de INTEGRAÇÃO,
 * não uma entidade de negócio.
 *
 * Estados: PENDING = negócio processado e resposta persistida, outbound ainda não
 * confirmado; SENT = resposta entregue. A resposta é preservada para permitir o
 * REENVIO (retry) do outbound numa re-entrega, sem reprocessar a conversa.
 */
public interface InboundReceiptStore {

    String STATUS_PENDING = "PENDING";
    String STATUS_SENT = "SENT";

    /** Estado durável do receipt para (provider, id), quando existir. */
    Optional<StoredReceipt> find(String provider, String externalMessageId);

    /**
     * Reivindica o processamento inserindo o receipt (status PENDING, ainda sem resposta)
     * e forçando o flush, de modo que a UNIQUE constraint serialize entregas concorrentes.
     * Deve ser chamado ANTES de avançar a conversa, no mesmo limite transacional.
     */
    void claimPending(String provider, String externalMessageId);

    /** Persiste a resposta gerada pela conversa (mantendo PENDING), no mesmo limite transacional. */
    void completeProcessing(String provider, String externalMessageId, String responseText);

    /** Marca como SENT após o envio outbound bem-sucedido (registra o id externo da resposta). */
    void markSent(String provider, String externalMessageId, String outboundMessageId);

    /** Visão mínima e neutra do receipt (não vaza a entidade JPA). */
    record StoredReceipt(String status, String responseText) {
    }
}
