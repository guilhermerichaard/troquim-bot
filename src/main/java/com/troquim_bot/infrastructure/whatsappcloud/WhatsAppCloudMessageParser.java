package com.troquim_bot.infrastructure.whatsappcloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.conversation.PhoneNumberNormalizer;
import com.troquim_bot.application.messaging.InboundMessageParser;
import com.troquim_bot.application.messaging.InboundTextMessage;
import com.troquim_bot.application.messaging.ParsedInboundPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte o payload do webhook da WhatsApp Cloud API no contrato interno neutro.
 * TODO o conhecimento do formato da Meta termina aqui (entry/changes/value/messages).
 *
 * Regras:
 * <ul>
 *   <li>processa apenas {@code object == "whatsapp_business_account"};</li>
 *   <li>suporta múltiplas entry/change/message no mesmo payload;</li>
 *   <li>somente {@code message.type == "text"} vira {@link InboundTextMessage};</li>
 *   <li>{@code statuses[]} e tipos não suportados são ignorados (sem ação de negócio);</li>
 *   <li>o telefone {@code from} é normalizado pelo componente já responsável
 *       ({@link PhoneNumberNormalizer}) — sem replicar normalização.</li>
 *   <li>PILOTO (canal único): {@code value.metadata.phone_number_id} deve corresponder ao
 *       {@code phone-number-id} configurado; se divergir, as mensagens daquele change são
 *       ignoradas (não chamam Conversation nem outbound). NÃO é multi-tenancy de canais.
 *       Futuramente, o par (provider, phone_number_id) resolverá o BusinessId do tenant.</li>
 * </ul>
 *
 * Não persiste o payload bruto completo.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudMessageParser implements InboundMessageParser {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudMessageParser.class);

    static final String PROVIDER = "whatsapp_cloud";
    private static final String WHATSAPP_OBJECT = "whatsapp_business_account";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsAppCloudProperties properties;

    public WhatsAppCloudMessageParser(WhatsAppCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public ParsedInboundPayload parse(byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new InboundPayloadFormatException("Payload WhatsApp Cloud nao desserializavel", e);
        }
        if (root == null || !root.isObject()) {
            throw new InboundPayloadFormatException("Payload WhatsApp Cloud vazio ou nao-objeto", null);
        }

        if (!WHATSAPP_OBJECT.equals(root.path("object").asText())) {
            return ParsedInboundPayload.ignored();
        }

        List<InboundTextMessage> textMessages = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                if (!phoneNumberIdMatchesPilot(value)) {
                    // phone_number_id divergente do piloto → ignora (sem receipt/Conversation/outbound).
                    // Log sanitizado: sem telefone/texto/id do canal (apenas o fato).
                    log.info("Evento WhatsApp Cloud ignorado: phone_number_id nao corresponde ao piloto configurado.");
                    continue;
                }
                for (JsonNode message : value.path("messages")) {
                    toTextMessage(message).ifPresent(textMessages::add);
                }
                // value.statuses[] é intencionalmente ignorado (status-only → sem ação).
            }
        }
        return new ParsedInboundPayload(true, textMessages);
    }

    private boolean phoneNumberIdMatchesPilot(JsonNode value) {
        String configured = properties.getPhoneNumberId();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        String incoming = textOrNull(value.path("metadata").path("phone_number_id"));
        return configured.equals(incoming);
    }

    private java.util.Optional<InboundTextMessage> toTextMessage(JsonNode message) {
        if (!"text".equals(message.path("type").asText())) {
            return java.util.Optional.empty();
        }
        String id = textOrNull(message.path("id"));
        String from = PhoneNumberNormalizer.normalizar(textOrNull(message.path("from")));
        String body = textOrNull(message.path("text").path("body"));
        if (id == null || from == null || body == null || body.isBlank()) {
            return java.util.Optional.empty();
        }
        long timestamp = parseEpoch(textOrNull(message.path("timestamp")));
        return java.util.Optional.of(new InboundTextMessage(PROVIDER, id, from, body, timestamp));
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
