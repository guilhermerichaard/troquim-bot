package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import org.springframework.stereotype.Service;

@Service
public class ContextService {

    public String montarContexto(String numero, String mensagem, IntentType intentType, String resumoEstado) {
        if (resumoEstado == null || resumoEstado.isBlank()) {
            return String.join(System.lineSeparator(),
                    "Estado atual do agendamento:",
                    "Step: INICIO",
                    "Conversa em andamento: não",
                    "Próxima informação necessária: serviço desejado",
                    "Última pergunta feita: não informado",
                    "Informações já coletadas:",
                    "Serviço: não informado",
                    "Dia: não informado",
                    "Horário: não informado",
                    "Nome: não informado"
            );
        }

        return resumoEstado;
    }
}
