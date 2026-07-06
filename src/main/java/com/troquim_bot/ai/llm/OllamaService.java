package com.troquim_bot.ai.llm;

import com.troquim_bot.ai.config.AiConfiguration;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final AiConfiguration aiConfiguration;

    public OllamaService(AiConfiguration aiConfiguration) {
        this.aiConfiguration = aiConfiguration;
    }

    public String responder(String mensagem) {
        try {
            String url = "http://localhost:11434/api/chat";

            Map<String, Object> body = Map.of(
                    "model", aiConfiguration.getModel(),
                    "stream", false,
                    "options", Map.of(
                            "temperature", aiConfiguration.getTemperature(),
                            "num_predict", aiConfiguration.getMaxTokens()
                    ),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "Você é um assistente educado, natural e objetivo. Responda em português do Brasil, com mensagens curtas e humanas."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", mensagem
                            )
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            Map message = (Map) response.getBody().get("message");

            return message.get("content").toString();
        } catch (Exception e) {
            System.err.println("OllamaService: erro ao comunicar com Ollama - " + e.getMessage());
            return null;
        }
    }
}