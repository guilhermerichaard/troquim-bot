package com.troquim_bot.infrastructure.whatsappcloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundFlowGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envio da mensagem interativa que abre um Flow, via WhatsApp Cloud API:
 * {@code POST {base}/{graph-api-version}/{phone-number-id}/messages} com
 * {@code type: interactive}, {@code interactive.type: flow}.
 *
 * INFRAESTRUTURA pura: traduz {@link FlowMessage} para o JSON da Graph API e mapeia erro
 * HTTP para exceção tipada. NENHUMA regra de agenda mora aqui — este adaptador não sabe
 * o que é um horário, um serviço ou um conflito.
 *
 * Condicional às DUAS flags (Cloud + Flow): sem qualquer uma delas o bean não existe, e
 * o caso de uso trata a ausência como "canal não suporta Flow".
 *
 * Nunca registra Authorization, token do Flow, telefone ou corpo — apenas status.
 */
@Component
@ConditionalOnWhatsAppCloud
@ConditionalOnWhatsAppFlow
public class WhatsAppCloudFlowGateway implements OutboundFlowGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudFlowGateway.class);

    /** Versão do protocolo de mensagem de Flow exigida pela Meta. */
    private static final String FLOW_MESSAGE_VERSION = "3";

    /**
     * Flow conduzido por Data Endpoint: a Meta chama o endpoint com {@code INIT} e usa a
     * resposta como primeira tela. Com "navigate" seria a mensagem a fixar tela e dados
     * iniciais, duplicando no envio uma decisão que já é do endpoint.
     */
    private static final String FLOW_ACTION_DATA_EXCHANGE = "data_exchange";

    private final RestClient restClient;
    private final WhatsAppCloudProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsAppCloudFlowGateway(RestClient whatsAppCloudRestClient,
                                    WhatsAppCloudProperties properties) {
        this.restClient = whatsAppCloudRestClient;
        this.properties = properties;
    }

    @Override
    public OutboundResult sendFlow(String toPhone, FlowMessage message) {
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", toPhone,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "flow",
                        "body", Map.of("text", message.corpo()),
                        "action", Map.of(
                                "name", "flow",
                                "parameters", parametros(message))));

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
            log.info("Mensagem de WhatsApp Flow enviada (status=sent, outboundId={}).", outboundId);
            return new OutboundResult(outboundId, "sent");
        } catch (RestClientResponseException httpError) {
            // Inclui o caso operacional conhecido: token expirado responde 401 com
            // code 190 / subcode 463. O corpo NAO e' registrado (pode trazer credencial).
            throw new WhatsAppCloudApiException(
                    "Graph API respondeu erro HTTP " + httpError.getStatusCode().value()
                            + " ao enviar mensagem de Flow",
                    httpError.getStatusCode().value(), httpError);
        } catch (WhatsAppCloudApiException alreadyTyped) {
            throw alreadyTyped;
        } catch (RuntimeException transportError) {
            throw new WhatsAppCloudApiException(
                    "Falha de transporte ao enviar mensagem de Flow: "
                            + transportError.getClass().getSimpleName(), null, transportError);
        }
    }

    private static Map<String, Object> parametros(FlowMessage message) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("flow_message_version", FLOW_MESSAGE_VERSION);
        parametros.put("flow_token", message.flowToken());
        // flow_id tem precedencia; flow_name existe para ambientes onde so o nome e' estavel.
        if (message.flowId() != null && !message.flowId().isBlank()) {
            parametros.put("flow_id", message.flowId());
        } else {
            parametros.put("flow_name", message.flowName());
        }
        parametros.put("flow_cta", message.cta());
        parametros.put("flow_action", FLOW_ACTION_DATA_EXCHANGE);
        // "draft" so' funciona para quem tem acesso ao app da Meta; producao usa "published".
        parametros.put("mode", message.modoRascunho() ? "draft" : "published");
        // Sem flow_action_payload: com data_exchange a primeira tela vem do INIT.
        return parametros;
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
            // 2xx sem o JSON esperado: sem id outbound (nao e' erro de envio).
        }
        return null;
    }
}
