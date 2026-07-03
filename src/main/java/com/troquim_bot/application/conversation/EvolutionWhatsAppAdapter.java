package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.evolution.EvolutionService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EvolutionWhatsAppAdapter implements WhatsAppAdapter {

    private final EvolutionService evolutionService;
    private final ObjectMapper objectMapper;

    public EvolutionWhatsAppAdapter(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<IncomingMessage> receberMensagem(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);

        String event = root.path("event").asText();
        if (!"messages.upsert".equals(event)) {
            return Optional.empty();
        }

        boolean fromMe = root.path("data").path("key").path("fromMe").asBoolean();
        if (fromMe) {
            return Optional.empty();
        }

        String messageId = root.path("data").path("key").path("id").asText();
        String remoteJid = root.path("data").path("key").path("remoteJid").asText();
        String sender = root.path("sender").asText();
        String mensagem = root.path("data").path("message").path("conversation").asText();

        if (mensagem == null || mensagem.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new IncomingMessage(messageId, remoteJid, sender, mensagem));
    }

    @Override
    public void enviarMensagem(String numero, String texto) {
        evolutionService.enviarMensagem(numero, texto);
    }
}
