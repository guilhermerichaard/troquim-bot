package com.troquim_bot.application.conversation;

import com.troquim_bot.conversation.ConversationService;
import org.springframework.stereotype.Component;

@Component
public class ConversationServiceMessageProcessor implements ConversationMessageProcessor {

    private final ConversationService conversationService;

    public ConversationServiceMessageProcessor(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public String gerarResposta(String numero, String mensagem) {
        return conversationService.gerarResposta(numero, mensagem);
    }
}
