package com.troquim_bot.ai.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PromptService {

    public String montarPrompt(String mensagem, String contexto) {

        try {

            ClassPathResource resource =
                    new ClassPathResource("prompts/system.txt");

            String prompt = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return prompt
                    .replace("{{mensagem}}", mensagem)
                    .replace("{{contexto}}", contexto);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar system.txt", e);
        }
    }
}