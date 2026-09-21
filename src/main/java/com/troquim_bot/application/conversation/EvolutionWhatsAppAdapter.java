package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.messaging.ConversationInteractivePresentation;
import com.troquim_bot.application.messaging.InboundFlowCompletion;
import com.troquim_bot.application.messaging.OutboundInteractiveOption;
import com.troquim_bot.evolution.EvolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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
        JsonNode messageNode = root.path("data").path("message");
        String mensagem = extrairMensagemOuRespostaRapida(messageNode);

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
    public void enviarLista(String numero, String texto, List<OutboundInteractiveOption> itens) {
        if (itens == null || itens.isEmpty()) {
            enviarMensagem(numero, texto);
            return;
        }

        String numeroNormalizado = WhatsAppContactResolver.normalizeForOutgoing(numero);
        List<Map<String, Object>> rows = itens.stream()
                .map(item -> Map.<String, Object>of(
                        "title", limitar(item.titulo(), 24),
                        "description", limitar(item.descricao() == null ? "" : item.descricao(), 72),
                        "rowId", item.id()))
                .toList();

        evolutionService.enviarLista(
                numeroNormalizado,
                "Troquim",
                texto,
                "Ver opcoes",
                List.of(Map.of(
                        "title", "Escolha uma opcao",
                        "rows", rows)));
    }

    private String limitar(String valor, int max) {
        if (valor == null || valor.length() <= max) {
            return valor == null ? "" : valor;
        }
        return valor.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public void enviarOpcoes(String numero, String texto, List<OutboundInteractiveOption> opcoes) {
        if (opcoes == null || opcoes.isEmpty() || opcoes.size() > 3) {
            enviarMensagem(numero, texto);
            return;
        }

        String numeroNormalizado = WhatsAppContactResolver.normalizeForOutgoing(numero);
        List<Map<String, Object>> botoes = opcoes.stream()
                .map(opcao -> Map.<String, Object>of(
                        "type", "reply",
                        "displayText", opcao.titulo(),
                        "id", opcao.id()))
                .toList();

        evolutionService.enviarBotoes(
                numeroNormalizado,
                "Troquim",
                texto,
                botoes);
    }

    private String extrairMensagemOuRespostaRapida(JsonNode message) {
        String conversation = message.path("conversation").asText(null);
        if (conversation != null && !conversation.isBlank()) {
            return conversation;
        }

        String selectedButton = message.path("buttonsResponseMessage")
                .path("selectedButtonId").asText(null);
        if (selectedButton != null && !selectedButton.isBlank()) {
            return mapearIdRespostaRapida(selectedButton);
        }

        String templateButton = message.path("templateButtonReplyMessage")
                .path("selectedId").asText(null);
        if (templateButton != null && !templateButton.isBlank()) {
            return mapearIdRespostaRapida(templateButton);
        }

        String listRow = message.path("listResponseMessage")
                .path("singleSelectReply").path("selectedRowId").asText(null);
        if (listRow != null && !listRow.isBlank()) {
            return mapearIdRespostaRapida(listRow);
        }

        JsonNode nativeFlow = message.path("interactiveResponseMessage")
                .path("nativeFlowResponseMessage");
        String paramsJson = nativeFlow.path("paramsJson").asText(null);
        if (paramsJson == null || paramsJson.isBlank()) {
            paramsJson = message.path("nativeFlowResponseMessage").path("paramsJson").asText(null);
        }
        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(paramsJson);
                for (String key : List.of("id", "selectedId", "selectedButtonId", "rowId")) {
                    String value = parsed.path(key).asText(null);
                    if (value != null && !value.isBlank()) {
                        return mapearIdRespostaRapida(value);
                    }
                }
            } catch (Exception ignorado) {
                // Payload interativo desconhecido: nao inventa intencao.
            }
        }

        return null;
    }

    private String mapearIdRespostaRapida(String id) {
        return ConversationInteractivePresentation.canonicalInput(id);
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
