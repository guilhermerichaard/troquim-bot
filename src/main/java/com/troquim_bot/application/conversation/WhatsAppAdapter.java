package com.troquim_bot.application.conversation;

import com.troquim_bot.application.messaging.InboundFlowCompletion;

import java.util.Optional;

public interface WhatsAppAdapter {

    Optional<IncomingMessage> receberMensagem(String payload) throws Exception;

    /**
     * Evento interativo de conclusao de Flow. Default vazio preserva adapters que
     * suportam apenas texto.
     */
    default Optional<InboundFlowCompletion> receberConclusaoFlow(String payload) throws Exception {
        return Optional.empty();
    }

    void enviarMensagem(String numero, String texto);

    record IncomingMessage(String messageId, String numero, String sender, String mensagem) {
    }
}
