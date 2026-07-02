package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.customer.CustomerProfile;
import org.springframework.stereotype.Service;

@Service
public class ContextService {

    public String montarContexto(String numero,
                                 String mensagem,
                                 IntentType intentType,
                                 String resumoEstado,
                                 CustomerProfile customerProfile) {
        String contextoAtendimento = resumoEstado;

        if (resumoEstado == null || resumoEstado.isBlank()) {
            contextoAtendimento = String.join(System.lineSeparator(),
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

        return String.join(System.lineSeparator(),
                "Cliente:",
                "Nome: " + valorOuNaoInformado(customerProfile != null ? customerProfile.getNome() : null),
                "Apelido: " + valorOuNaoInformado(customerProfile != null ? customerProfile.getApelido() : null),
                "Número: " + valorOuNaoInformado(customerProfile != null ? customerProfile.getNumero() : numero),
                "Total de atendimentos: " + (customerProfile != null ? customerProfile.getTotalAtendimentos() : 0),
                "Último atendimento: " + valorOuNaoInformado(customerProfile != null && customerProfile.getUltimoAtendimento() != null
                        ? customerProfile.getUltimoAtendimento().toString()
                        : null),
                "",
                contextoAtendimento
        );
    }

    private String valorOuNaoInformado(String valor) {
        return valor == null || valor.isBlank() ? "não informado" : valor;
    }
}
