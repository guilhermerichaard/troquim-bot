package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.messaging.InboundFlowCompletion;
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
    public Optional<InboundFlowCompletion> receberConclusaoFlow(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);

        if (!"messages.upsert".equals(root.path("event").asText())) {
            return Optional.empty();
        }
        if (root.path("data").path("key").path("fromMe").asBoolean()) {
            return Optional.empty();
        }

        String messageId = root.path("data").path("key").path("id").asText();
        String numero = WhatsAppContactResolver.resolveContactNumber(root);
        if (messageId == null || messageId.isBlank() || numero == null || numero.isBlank()) {
            return Optional.empty();
        }

        JsonNode message = root.path("data").path("message");
        String flowToken = extrairFlowToken(message);
        if (flowToken == null || flowToken.isBlank()) {
            return Optional.empty();
        }

        long timestamp = root.path("data").path("messageTimestamp").asLong(0L);
        return Optional.of(new InboundFlowCompletion(
                "evolution", messageId, numero, flowToken, timestamp));
    }

    /**
     * Evolution/Baileys pode variar o envelope da resposta interativa entre versoes.
     * Procuramos apenas dentro de data.message e aceitamos valores JSON em campos
     * conhecidos (paramsJson/response_json); a validade REAL do token sera checada
     * pela FlowSession emitida pelo Troquim.
     */
    private String extrairFlowToken(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            JsonNode direct = node.get("flow_token");
            if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
                return direct.asText();
            }

            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if (("paramsJson".equals(key) || "response_json".equals(key))
                        && value != null && value.isTextual()) {
                    String token = extrairFlowTokenDeJsonString(value.asText());
                    if (token != null) {
                        return token;
                    }
                }

                String nested = extrairFlowToken(value);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = extrairFlowToken(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String extrairFlowTokenDeJsonString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return extrairFlowToken(objectMapper.readTree(raw));
        } catch (Exception ignorado) {
            return null;
        }
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
