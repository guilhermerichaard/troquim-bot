package com.troquim_bot.application.conversation;

import com.troquim_bot.application.messaging.InboundFlowCompletion;

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
    default void enviarOpcoes(String numero, String texto, List<QuickReply> opcoes) {
        enviarMensagem(numero, texto);
    }

    default void enviarLista(String numero, String texto, List<ListItem> itens) {
        enviarMensagem(numero, texto);
    }

    record ListItem(String id, String titulo, String descricao) {
        public ListItem {
            if (id == null || id.isBlank() || titulo == null || titulo.isBlank()) {
                throw new IllegalArgumentException("List item exige id e titulo");
            }
        }
    }

    record QuickReply(String id, String titulo) {
        public QuickReply {
            if (id == null || id.isBlank() || titulo == null || titulo.isBlank()) {
                throw new IllegalArgumentException("Quick reply exige id e titulo");
            }
        }
    }

    record IncomingMessage(String messageId, String numero, String sender, String mensagem) {
    }
}
