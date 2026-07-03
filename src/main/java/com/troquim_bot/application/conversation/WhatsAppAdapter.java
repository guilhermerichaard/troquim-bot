package com.troquim_bot.application.conversation;

import java.util.Optional;

public interface WhatsAppAdapter {

    Optional<IncomingMessage> receberMensagem(String payload) throws Exception;

    void enviarMensagem(String numero, String texto);

    record IncomingMessage(String messageId, String numero, String sender, String mensagem) {
    }
}
