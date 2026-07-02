package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.memory.ConversationMessage;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfile;
import com.troquim_bot.customer.CustomerProfileService;
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
    private final CustomerProfileService customerProfileService;

    public ConversationService(IntentService intentService,
                               QuickResponseService quickResponseService,
                               ContextService contextService,
                               ConversationStateService conversationStateService,
                               ConversationMemory conversationMemory,
                               OllamaService ollamaService,
                               PromptService promptService,
                               CustomerProfileService customerProfileService) {
        this.intentService = intentService;
        this.quickResponseService = quickResponseService;
        this.contextService = contextService;
        this.conversationStateService = conversationStateService;
        this.conversationMemory = conversationMemory;
        this.ollamaService = ollamaService;
        this.promptService = promptService;
        this.customerProfileService = customerProfileService;
    }

    public String gerarResposta(String numero, String mensagem) {

        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        IntentType intentType = intentService.classificar(mensagem);
        boolean novoAtendimento = deveIniciarNovoAtendimento(numero, intentType);
        if (novoAtendimento && conversationStateService.possuiEstado(numero)) {
            conversationStateService.limparEstado(numero);
        }

        CustomerProfile customerProfile = novoAtendimento
                ? customerProfileService.iniciarAtendimento(numero)
                : customerProfileService.buscarOuCriar(numero);

        String nomePreferido = customerProfileService.nomePreferido(customerProfile).orElse(null);
        Optional<String> nomeInformado = conversationStateService.extrairNomeInformado(mensagem);
        ConversationState conversationState = conversationStateService.processarMensagem(numero, mensagem, nomePreferido);
        customerProfile = sincronizarNome(numero, nomeInformado, conversationState, customerProfile);

        Optional<String> respostaRapida = quickResponseService.buscarResposta(intentType);

        if (respostaRapida.isPresent() && deveUsarRespostaRapida(intentType, conversationState, mensagem)) {
            String resposta = intentType == IntentType.SAUDACAO
                    ? montarSaudacao(customerProfile)
                    : respostaRapida.get();
            return responderComMemoria(numero, mensagem, resposta);
        }

        if (intentType == IntentType.CONSULTAR_NOME && nomeInformado.isEmpty()) {
            return responderComMemoria(numero, mensagem, montarRespostaNome(customerProfile));
        }

        Optional<String> respostaEstado = conversationStateService.montarRespostaAutomatica(conversationState, mensagem);

        if (respostaEstado.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaEstado.get());
        }

        // Verifica se há resposta específica para a intenção detectada
        Optional<String> respostaPorIntencao = conversationStateService.montarRespostaPorIntencao(conversationState, mensagem, intentType);

        if (respostaPorIntencao.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaPorIntencao.get());
        }

        // Se perguntou sobre agendamentos, retorna a lista
        if (conversationStateService.isPerguntaSobreAgendamentos(mensagem)) {
            String lista = conversationStateService.listarAgendamentosPendentes(conversationState);
            return responderComMemoria(numero, mensagem, lista);
        }

        return gerarRespostaComOllama(numero, mensagem, intentType, conversationState, customerProfile);
    }

    private boolean deveIniciarNovoAtendimento(String numero, IntentType intentType) {
        if (!conversationStateService.possuiEstado(numero)) {
            return true;
        }

        ConversationState conversationState = conversationStateService.buscarPorNumero(numero);
        return intentType == IntentType.SAUDACAO
                && !conversationStateService.conversaEmAndamento(conversationState);
    }

    private CustomerProfile sincronizarNome(String numero,
                                            Optional<String> nomeInformado,
                                            ConversationState conversationState,
                                            CustomerProfile customerProfile) {
        if (nomeInformado.isPresent()) {
            conversationStateService.atualizarNome(numero, nomeInformado.get());
            return customerProfileService.salvarNome(numero, nomeInformado.get());
        }

        String nomeAtendimento = conversationState.getNome();
        if (nomeAtendimento != null && !nomeAtendimento.isBlank()) {
            String nomePerfil = customerProfile.getNome();
            if (nomePerfil == null || !nomePerfil.equals(nomeAtendimento)) {
                return customerProfileService.salvarNome(numero, nomeAtendimento);
            }
        }

        return customerProfile;
    }

    private String montarSaudacao(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Boa tarde, " + nome + "! Como posso ajudar?")
                .orElse("Boa tarde! Como posso ajudar?");
    }

    private String montarRespostaNome(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Seu nome está salvo como " + nome + ".")
                .orElse("Você ainda não informou seu nome. Como prefere que eu te chame?");
    }

    private boolean deveUsarRespostaRapida(IntentType intentType, ConversationState conversationState, String mensagem) {
        if (intentType == IntentType.SAUDACAO) {
            return !conversationStateService.conversaEmAndamento(conversationState);
        }

        // Se for AGRADECIMENTO, só usar resposta rápida se for APENAS agradecimento curto
        if (intentType == IntentType.AGRADECIMENTO) {
            return conversationStateService.isApenasAgradecimentoCurto(mensagem);
        }

        return true;
    }

    private String gerarRespostaComOllama(String numero,
                                          String mensagem,
                                          IntentType intentType,
                                          ConversationState conversationState,
                                          CustomerProfile customerProfile) {
        String resumoEstado = conversationStateService.montarResumo(conversationState);
        String contexto = contextService.montarContexto(numero, mensagem, intentType, resumoEstado, customerProfile);
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
