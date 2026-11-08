package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.application.conversation.BusinessConversationFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QuickResponseService {

    private final BusinessConversationFacts businessFacts;

    /** Compatibilidade para testes legados que constroem o servico manualmente. */
    public QuickResponseService() {
        this(null);
    }

    @Autowired
    public QuickResponseService(BusinessConversationFacts businessFacts) {
        this.businessFacts = businessFacts;
    }

    public Optional<String> buscarResposta(IntentType intentType) {
        return switch (intentType) {
            case SAUDACAO -> Optional.of("Oi! Me conta o que você precisa.");
            case AGRADECIMENTO -> Optional.of("Imagina! Qualquer coisa é só chamar.");
            case DESPEDIDA -> Optional.of("Até mais! Quando precisar, é só chamar.");
            case HUMANO -> Optional.of("Entendi. Para falar com uma pessoa, alguém do estabelecimento precisa assumir a conversa no WhatsApp. Enquanto isso, posso continuar te ajudando por aqui.");
            case CONSULTAR_SERVICOS -> servicosReais();
            case CONSULTAR_HORARIOS -> horarioReal();
            case CONSULTAR_ENDERECO -> enderecoReal();
            case CONSULTAR_QUEM_SOU -> quemSou();
            default -> Optional.empty();
        };
    }

    private Optional<String> servicosReais() {
        if (businessFacts == null) {
            return Optional.empty();
        }
        var servicos = businessFacts.services();
        if (servicos.isEmpty()) {
            return Optional.of("O catálogo ainda não está configurado por aqui.");
        }
        String nomes = servicos.stream()
                .map(BusinessConversationFacts.ServiceFact::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return Optional.of("Hoje temos " + nomes + ". Qual deles você quer ver?");
    }

    private Optional<String> horarioReal() {
        if (businessFacts == null) {
            return Optional.empty();
        }
        return businessFacts.openingHoursSummary()
                .map(value -> "Nosso horário cadastrado é: " + value + ".");
    }

    private Optional<String> enderecoReal() {
        if (businessFacts == null) {
            return Optional.empty();
        }
        return businessFacts.address().map(value -> "Ficamos em " + value + ".");
    }

    private Optional<String> quemSou() {
        if (businessFacts == null) {
            return Optional.of("Sou o atendimento do estabelecimento por aqui.");
        }
        String nome = businessFacts.publicName().orElse("estabelecimento");
        return Optional.of("Sou o atendimento do " + nome
                + " por aqui. Posso consultar a agenda real e cuidar do seu agendamento.");
    }
}
