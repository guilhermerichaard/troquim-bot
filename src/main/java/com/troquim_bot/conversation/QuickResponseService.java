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
            default -> Optional.empty();
        };
    }
}
