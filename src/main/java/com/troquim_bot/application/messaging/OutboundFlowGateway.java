package com.troquim_bot.application.messaging;

/**
 * Porta provider-neutral de envio de mensagem interativa que abre um Flow.
 *
 * Separada de {@link OutboundMessageGateway} de propósito: nem todo canal suporta Flow.
 * Quando a capacidade não existe, o bean simplesmente não é criado, e o caso de uso cai
 * no atendimento textual — em vez de descobrirmos em runtime que um método da interface
 * não faz nada.
 *
 * Falhas de transporte/HTTP viram exceção tipada de infraestrutura. Segredos e conteúdo
 * pessoal nunca são registrados.
 */
public interface OutboundFlowGateway {

    /**
     * Envia a mensagem que abre o Flow.
     *
     * @throws RuntimeException tipada pela infraestrutura quando o envio falha
     */
    OutboundResult sendFlow(String toPhone, FlowMessage message);
}
