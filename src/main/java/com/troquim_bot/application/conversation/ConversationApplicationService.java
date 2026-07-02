package com.troquim_bot.application.conversation;

import com.troquim_bot.conversation.ConversationService;
import org.springframework.stereotype.Service;

@Service
public class ConversationApplicationService {

    private final ConversationService conversationService;

    public ConversationApplicationService(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public String processarMensagem(String numero, String mensagem) {
        return conversationService.gerarResposta(numero, mensagem);
    }
}