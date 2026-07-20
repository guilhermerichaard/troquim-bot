package com.troquim_bot.application.messaging;

/**
 * Porta provider-neutral de envio de mensagens. A implementação (infraestrutura)
 * fala com a API do provedor (Graph API). Erros de transporte/HTTP viram exceção
 * tipada de infraestrutura; segredos e corpo pessoal nunca são registrados.
 */
public interface OutboundMessageGateway {

    /**
     * Envia uma mensagem de texto ao destinatário.
     *
     * @param toPhone telefone do destinatário
     * @param text    corpo textual
     * @return resultado com o id externo (quando disponível)
     */
    OutboundResult sendText(String toPhone, String text);
}
