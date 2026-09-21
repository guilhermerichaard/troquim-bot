package com.troquim_bot.infrastructure.whatsappcloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.messaging.OutboundInteractiveOption;
import com.troquim_bot.application.messaging.OutboundMessageGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Cliente da WhatsApp Cloud API para envio de texto:
 * {@code POST {base}/{graph-api-version}/{phone-number-id}/messages} com
 * {@code Authorization: Bearer <access-token>}.
 *
 * Erros HTTP viram {@link WhatsAppCloudApiException} (tipada). Nunca registra
 * Authorization nem o corpo (dado pessoal) — apenas status/id sanitizados.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudOutboundGateway implements OutboundMessageGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudOutboundGateway.class);

    private final RestClient restClient;
    private final WhatsAppCloudProperties properties;
    // ObjectMapper próprio (Jackson 2), como no restante do projeto — evita depender
    // do converter de resposta do RestClient.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsAppCloudOutboundGateway(RestClient whatsAppCloudRestClient,
                                        WhatsAppCloudProperties properties) {
        this.restClient = whatsAppCloudRestClient;
        this.properties = properties;
    }

    @Override
    public OutboundResult sendText(String toPhone, String text) {
        Map<String, Object> payload = basePayload(
                toPhone,
                "text",
                Map.of("preview_url", false, "body", text));
        return sendPayload(payload);
    }

    @Override
    public OutboundResult sendButtons(String toPhone, String text,
                                      List<OutboundInteractiveOption> options) {
        if (options == null || options.isEmpty() || options.size() > 3) {
            return sendText(toPhone, text);
        }

        List<Map<String, Object>> buttons = options.stream()
                .map(option -> Map.<String, Object>of(
                        "type", "reply",
                        "reply", Map.of(
                                "id", option.id(),
                                "title", limit(option.title(), 20))))
                .toList();

        Map<String, Object> interactive = Map.of(
                "type", "button",
                "body", Map.of("text", text),
                "action", Map.of("buttons", buttons));

        return sendPayload(basePayload(toPhone, "interactive", interactive));
    }

    @Override
    public OutboundResult sendList(String toPhone, String text,
                                   List<OutboundInteractiveOption> options) {
        if (options == null || options.size() < 2 || options.size() > 10) {
            return sendText(toPhone, text);
        }

        List<Map<String, Object>> rows = options.stream()
                .map(option -> {
                    java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", option.id());
                    row.put("title", limit(option.title(), 24));
                    if (option.description() != null && !option.description().isBlank()) {
                        row.put("description", limit(option.description(), 72));
                    }
                    return Map.copyOf(row);
                })
                .toList();

        Map<String, Object> interactive = Map.of(
                "type", "list",
                "body", Map.of("text", text),
                "action", Map.of(
                        "button", "Ver opcoes",
                        "sections", List.of(Map.of(
                                "title", "Escolha uma opcao",
                                "rows", rows))));

        return sendPayload(basePayload(toPhone, "interactive", interactive));
    }

    private Map<String, Object> basePayload(String toPhone, String type, Object body) {
        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", toPhone,
                "type", type,
                type, body);
    }

    private OutboundResult sendPayload(Map<String, Object> payload) {
        try {
            String responseBody = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            properties.getGraphApiVersion(), properties.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            String outboundId = extractMessageId(responseBody);
            log.info("Outbound WhatsApp Cloud enviado (status=sent, outboundId={}).", outboundId);
            return new OutboundResult(outboundId, "sent");
        } catch (RestClientResponseException httpError) {
            // 4xx/5xx — status conhecido, sem expor corpo/credencial.
            throw new WhatsAppCloudApiException(
                    "Graph API respondeu erro HTTP " + httpError.getStatusCode().value(),
                    httpError.getStatusCode().value(), httpError);
        } catch (WhatsAppCloudApiException alreadyTyped) {
            throw alreadyTyped;
        } catch (RuntimeException transportError) {
            // Timeout/conexão/transporte (ex: ResourceAccessException).
            throw new WhatsAppCloudApiException(
                    "Falha de transporte ao chamar a Graph API: "
                            + transportError.getClass().getSimpleName(), null, transportError);
        }
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode messages = response.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                JsonNode id = messages.get(0).path("id");
                return id.isMissingNode() || id.isNull() ? null : id.asText();
            }
        } catch (Exception ignored) {
            // Resposta 2xx sem JSON esperado: sem id outbound (não é erro de envio).
        }
        return null;
    }
}
