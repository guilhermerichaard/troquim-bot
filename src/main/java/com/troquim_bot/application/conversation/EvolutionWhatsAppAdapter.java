package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.evolution.EvolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EvolutionWhatsAppAdapter implements WhatsAppAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionWhatsAppAdapter.class);

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
        String sender = rawSender(root.path("sender").asText());
        String mensagem = root.path("data").path("message").path("conversation").asText();

        if (mensagem == null || mensagem.isBlank()) {
            return Optional.empty();
        }

        String numero = WhatsAppContactResolver.resolveContactNumber(root);
        logger.info("remoteJid: {}, remoteJidAlt: {}, sender: {}, numero resolvido: {}",
            root.path("data").path("key").path("remoteJid").asText(),
            root.path("data").path("remoteJidAlt").asText(),
            root.path("sender").asText(),
            numero);

        if (numero == null) {
            logger.warn("Nao foi possivel resolver numero do contato. Payload: {}", payload);
            return Optional.empty();
        }

        return Optional.of(new IncomingMessage(messageId, numero, sender, mensagem));
    }

    @Override
    public void enviarMensagem(String numero, String texto) {
        String numeroNormalizado = WhatsAppContactResolver.normalizeForOutgoing(numero);
        logger.info("Enviando mensagem para numero normalizado: {} (original: {})", numeroNormalizado, numero);
        evolutionService.enviarMensagem(numeroNormalizado, texto);
    }

    private String rawSender(String sender) {
        return sender == null ? null : WhatsAppContactResolver.normalizeForOutgoing(sender);
    }
}
