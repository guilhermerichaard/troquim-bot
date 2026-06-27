package com.troquim_bot.conversation;

import com.troquim_bot.ai.OllamaService;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final OllamaService ollamaService;

    public ConversationService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    public String gerarResposta(String numero, String mensagem) {

        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        return ollamaService.responder(mensagem);
    }
}