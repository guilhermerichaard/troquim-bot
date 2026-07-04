package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QuickResponseService {

    public Optional<String> buscarResposta(IntentType intentType) {
        return switch (intentType) {
            case SAUDACAO -> Optional.of("Boa tarde! Como posso ajudar?");
            case AGRADECIMENTO -> Optional.of("Disponha!");
            case DESPEDIDA -> Optional.of("Até mais! Sempre que precisar estou por aqui.");
            case HUMANO -> Optional.of("Perfeito. Vou encaminhar sua mensagem para confirmação.");
            case CONSULTAR_SERVICOS -> Optional.of("Trabalhamos com corte de cabelo, manicure, pedicure, escova e hidratação. Qual serviço você tem interesse?");
            case CONSULTAR_HORARIOS -> Optional.of("Funcionamos de segunda a sexta, das 8h às 18h, e aos sábados das 8h às 13h.");
            case CONSULTAR_ENDERECO -> Optional.of("Estamos na Rua Augusta, 1500, Consolação, São Paulo.");
            case CONSULTAR_QUEM_SOU -> Optional.of("Sou o assistente virtual do salão Troquim. Estou aqui para ajudar com agendamentos e tirar suas dúvidas!");
            default -> Optional.empty();
        };
    }
}
