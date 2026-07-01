package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.prompt.PromptService;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final IntentService intentService;
    private final ContextService contextService;
    private final OllamaService ollamaService;
    private final PromptService promptService;

    public ConversationService(IntentService intentService,
                               ContextService contextService,
                               OllamaService ollamaService,
                               PromptService promptService) {
        this.intentService = intentService;
        this.contextService = contextService;
        this.ollamaService = ollamaService;
        this.promptService = promptService;
    }

    public String gerarResposta(String numero, String mensagem) {

        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        IntentType intentType = intentService.classificar(mensagem);
        String contexto = contextService.montarContexto(numero, mensagem, intentType);
        String prompt = promptService.montarPrompt(mensagem, contexto);

        return ollamaService.responder(prompt);
    }
}
