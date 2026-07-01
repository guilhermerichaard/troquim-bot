package com.troquim_bot.ai.prompt;

import com.troquim_bot.ai.memory.ConversationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PromptService {

    public String montarPrompt(String mensagem, String contexto, List<ConversationMessage> historico) {

        try {

            ClassPathResource resource =
                    new ClassPathResource("prompts/system.txt");

            String prompt = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return prompt
                    .replace("{{mensagem}}", mensagem)
                    .replace("{{contexto}}", contexto)
                    .replace("{{historico}}", formatarHistorico(historico));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar system.txt", e);
        }
    }

    private String formatarHistorico(List<ConversationMessage> historico) {
        if (historico == null || historico.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (ConversationMessage message : historico) {
            builder
                    .append(formatarRole(message.getRole()))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(message.getContent())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        return builder.toString().trim();
    }

    private String formatarRole(String role) {
        if ("user".equals(role)) {
            return "Usuário:";
        }

        if ("assistant".equals(role)) {
            return "Assistente:";
        }

        return role + ":";
    }
}
