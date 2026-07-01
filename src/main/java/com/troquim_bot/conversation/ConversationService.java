package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.memory.ConversationMessage;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private final IntentService intentService;
    private final QuickResponseService quickResponseService;
    private final ContextService contextService;
    private final ConversationStateService conversationStateService;
    private final ConversationMemory conversationMemory;
    private final OllamaService ollamaService;
    private final PromptService promptService;

    public ConversationService(IntentService intentService,
                               QuickResponseService quickResponseService,
                               ContextService contextService,
                               ConversationStateService conversationStateService,
                               ConversationMemory conversationMemory,
                               OllamaService ollamaService,
                               PromptService promptService) {
        this.intentService = intentService;
        this.quickResponseService = quickResponseService;
        this.contextService = contextService;
        this.conversationStateService = conversationStateService;
        this.conversationMemory = conversationMemory;
        this.ollamaService = ollamaService;
        this.promptService = promptService;
    }

    public String gerarResposta(String numero, String mensagem) {

        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        IntentType intentType = intentService.classificar(mensagem);
        ConversationState conversationState = conversationStateService.processarMensagem(numero, mensagem);

        Optional<String> respostaRapida = quickResponseService.buscarResposta(intentType);

        if (respostaRapida.isPresent() && deveUsarRespostaRapida(intentType, conversationState)) {
            return responderComMemoria(numero, mensagem, respostaRapida.get());
        }

        Optional<String> respostaEstado = conversationStateService.montarRespostaAutomatica(conversationState, mensagem);

        if (respostaEstado.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaEstado.get());
        }

        return gerarRespostaComOllama(numero, mensagem, intentType, conversationState);
    }

    private boolean deveUsarRespostaRapida(IntentType intentType, ConversationState conversationState) {
        if (intentType == IntentType.SAUDACAO) {
            return !conversationStateService.conversaEmAndamento(conversationState);
        }

        return true;
    }

    private String gerarRespostaComOllama(String numero, String mensagem, IntentType intentType, ConversationState conversationState) {
        String resumoEstado = conversationStateService.montarResumo(conversationState);
        String contexto = contextService.montarContexto(numero, mensagem, intentType, resumoEstado);
        List<ConversationMessage> historico = conversationMemory.getConversation(numero);

        conversationMemory.addUserMessage(numero, mensagem);

        String prompt = promptService.montarPrompt(mensagem, contexto, historico);
        String resposta = ollamaService.responder(prompt);

        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
    }

    private String responderComMemoria(String numero, String mensagem, String resposta) {
        conversationMemory.addUserMessage(numero, mensagem);
        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
    }
}
