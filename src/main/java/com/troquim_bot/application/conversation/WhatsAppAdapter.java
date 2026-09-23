package com.troquim_bot.application.conversation;

import com.troquim_bot.application.messaging.InboundFlowCompletion;
import com.troquim_bot.application.messaging.OutboundInteractiveOption;

import java.util.List;
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

    /**
     * Acoes rapidas provider-neutral. Providers sem suporte interativo preservam a
     * experiencia enviando o mesmo texto; o Domain/Conversation continua entendendo
     * os valores digitados normalmente.
     */
    default void enviarOpcoes(String numero, String texto, List<OutboundInteractiveOption> opcoes) {
        enviarMensagem(numero, texto);
    }

    default void enviarLista(String numero, String texto, List<OutboundInteractiveOption> itens) {
        enviarMensagem(numero, texto);
    }

    record IncomingMessage(String messageId, String numero, String sender, String mensagem,
                           String profileName) {
        public IncomingMessage(String messageId, String numero, String sender, String mensagem) {
            this(messageId, numero, sender, mensagem, null);
        }
    }
}
