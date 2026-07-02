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
        CustomerProfile customerProfile = carregarPerfil(numero, intentType);
        String nomePreferido = customerProfileService.nomePreferido(customerProfile).orElse(null);
        ConversationState conversationState = conversationStateService.buscarPorNumero(numero, nomePreferido);
        Optional<String> nomeInformado = conversationStateService.extrairNomeInformado(mensagem);
        ConversationRoute route = rotearMensagem(conversationState, mensagem, intentType);

        if (route.continuaFluxo()) {
            conversationState = conversationStateService.processarMensagem(numero, mensagem, nomePreferido);
        }

        customerProfile = sincronizarNome(numero, nomeInformado, conversationState, customerProfile);

        Optional<String> respostaIntencao = executarIntencao(
                route,
                mensagem,
                conversationState,
                customerProfile,
                nomeInformado
        );
        if (respostaIntencao.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaIntencao.get());
        }

        Optional<String> respostaFluxo = executarFluxo(route, conversationState, mensagem);
        if (respostaFluxo.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaFluxo.get());
        }

        return gerarRespostaComOllama(numero, mensagem, intentType, conversationState, customerProfile);
    }

    private CustomerProfile carregarPerfil(String numero, IntentType intentType) {
        if (deveIniciarNovoAtendimento(numero, intentType)) {
            return customerProfileService.iniciarAtendimento(numero);
        }

        return customerProfileService.buscarOuCriar(numero);
    }

    private ConversationRoute rotearMensagem(ConversationState conversationState,
                                             String mensagem,
                                             IntentType intentType) {
        boolean continuaFluxo = conversationStateService.deveContinuarFluxo(conversationState, mensagem, intentType);
        return new ConversationRoute(intentType, continuaFluxo);
    }

    private Optional<String> executarIntencao(ConversationRoute route,
                                             String mensagem,
                                             ConversationState conversationState,
                                             CustomerProfile customerProfile,
                                             Optional<String> nomeInformado) {
        IntentType intentType = route.intentType();
        Optional<String> respostaRapida = quickResponseService.buscarResposta(intentType);

        if (respostaRapida.isPresent() && deveUsarRespostaRapida(intentType, mensagem)) {
            if (intentType == IntentType.SAUDACAO) {
                return Optional.of(montarSaudacao(customerProfile));
            }

            return respostaRapida;
        }

        if (intentType == IntentType.LEMBRAR_CLIENTE) {
            return Optional.of(montarRespostaLembranca(customerProfile));
        }

        if (intentType == IntentType.CONSULTAR_NOME) {
            if (nomeInformado.isPresent()) {
                return Optional.of("Perfeito, " + nomeInformado.get() + ". Vou lembrar.");
            }

            return Optional.of(montarRespostaNome(customerProfile));
        }

        Optional<String> respostaPorIntencao = conversationStateService.montarRespostaPorIntencao(
                conversationState,
                mensagem,
                intentType
        );
        if (respostaPorIntencao.isPresent()) {
            return respostaPorIntencao;
        }

        if (conversationStateService.isPerguntaSobreAgendamentos(mensagem)) {
            return Optional.of(conversationStateService.listarAgendamentosPendentes(conversationState));
        }

        return Optional.empty();
    }

    private Optional<String> executarFluxo(ConversationRoute route,
                                           ConversationState conversationState,
                                           String mensagem) {
        if (!route.continuaFluxo()) {
            return Optional.empty();
        }

        return conversationStateService.montarRespostaAutomatica(conversationState, mensagem);
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

    private String montarRespostaLembranca(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Lembro sim, " + nome + ". Como posso ajudar?")
                .orElse("Ainda não tenho seu nome salvo. Como prefere que eu te chame?");
    }

    private boolean deveUsarRespostaRapida(IntentType intentType, String mensagem) {
        if (intentType == IntentType.SAUDACAO) {
            return true;
        }

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

    private record ConversationRoute(IntentType intentType, boolean continuaFluxo) {
    }
}
