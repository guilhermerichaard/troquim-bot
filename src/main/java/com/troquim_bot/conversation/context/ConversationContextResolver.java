package com.troquim_bot.conversation.context;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfile;
import com.troquim_bot.customer.CustomerProfileService;

import java.util.Optional;

public class ConversationContextResolver {

    private final ConversationStateService conversationStateService;
    private final CustomerProfileService customerProfileService;

    public ConversationContextResolver(ConversationStateService conversationStateService,
                                       CustomerProfileService customerProfileService) {
        this.conversationStateService = conversationStateService;
        this.customerProfileService = customerProfileService;
    }

    public CustomerProfile carregarPerfil(String numero, IntentType intentType) {
        if (deveIniciarNovoAtendimento(numero, intentType)) {
            return customerProfileService.iniciarAtendimento(numero);
        }

        return customerProfileService.buscarOuCriar(numero);
    }

    public boolean deveIniciarNovoAtendimento(String numero, IntentType intentType) {
        if (!conversationStateService.possuiEstado(numero)) {
            return true;
        }

        ConversationState conversationState = conversationStateService.buscarPorNumero(numero);
        return intentType == IntentType.SAUDACAO
                && !conversationStateService.conversaEmAndamento(conversationState);
    }

    public CustomerProfile sincronizarNome(String numero,
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

    public String montarSaudacao(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Boa tarde, " + nome + "! Como posso ajudar?")
                .orElse("Boa tarde! Como posso ajudar?");
    }

    public String montarRespostaNome(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Seu nome est\u00E1 salvo como " + nome + ".")
                .orElse("Voc\u00EA ainda n\u00E3o informou seu nome. Como prefere que eu te chame?");
    }

    public String montarRespostaLembranca(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Lembro sim, " + nome + ". Como posso ajudar?")
                .orElse("Ainda n\u00E3o tenho seu nome salvo. Como prefere que eu te chame?");
    }
}
