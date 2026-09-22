package com.troquim_bot.infrastructure.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.conversation.BookingIntent;
import com.troquim_bot.conversation.BookingIntentInterpreter;
import com.troquim_bot.conversation.HeuristicBookingIntentInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter OpenAI para interpretação de intenção.
 *
 * A IA NUNCA consulta catálogo, agenda ou profissionais e NUNCA confirma reserva.
 * Ela apenas converte linguagem natural em {@link BookingIntent}. Toda decisão continua
 * no ConversationBookingGateway/Domain.
 *
 * Fail-open controlado: API desligada, sem chave, timeout, erro HTTP, recusa ou JSON
 * inválido caem no interpretador heurístico atual.
 */
@Component
@Primary
public class OpenAiBookingIntentInterpreter implements BookingIntentInterpreter {

    private static final Logger log =
            LoggerFactory.getLogger(OpenAiBookingIntentInterpreter.class);

    private static final String SYSTEM_PROMPT = """
            Você extrai intenção de agendamento para um sistema brasileiro de agenda.

            Sua única tarefa é interpretar o que o cliente escreveu. Você NÃO pode:
            - inventar serviço, profissional, disponibilidade ou horário;
            - afirmar que algo está disponível;
            - decidir ou confirmar agendamento;
            - tratar consulta/cancelamento de agendamento existente como novo agendamento.

            Retorne booking_intent=true somente quando a mensagem expressar intenção de
            marcar/fazer um serviço, repetir o serviço habitual ou informar preferências
            de um novo agendamento.

            Campos:
            - service_query: somente a expressão de serviço dita pelo cliente, sem frases
              como "quero", "marcar", dia ou horário. Vazio se não informado.
            - day_query: "", "hoje", "amanha", nome do dia da semana sem acento
              (segunda, terca, quarta, quinta, sexta, sabado, domingo) ou data ISO yyyy-MM-dd.
            - earliest_time/latest_time/target_time: HH:mm ou vazio.
            - same_as_usual=true somente se o cliente explicitamente pedir "o mesmo de sempre"
              ou equivalente.

            Convenções de período:
            - manhã: 08:00 a 11:59, alvo 10:00
            - tarde: 12:00 a 17:59, alvo 15:00
            - noite: 18:00 a 23:59, alvo 19:00
            - "depois/a partir das X": earliest_time=X, target_time=X
            - "antes das X": latest_time=X, target_time=X
            - horário exato: earliest_time=latest_time=target_time

            Não complete informação ausente. Não transforme saudação, cancelamento,
            consulta de agendamentos ou conversa casual em booking_intent.
            """;

    private final OpenAiBookingIntentProperties properties;
    private final ObjectMapper objectMapper;
    private final HeuristicBookingIntentInterpreter fallback;
    private final RelogioDoNegocio relogio;
    private final HttpClient httpClient;

    public OpenAiBookingIntentInterpreter(OpenAiBookingIntentProperties properties,
                                          ObjectMapper objectMapper,
                                          HeuristicBookingIntentInterpreter fallback,
                                          RelogioDoNegocio relogio) {
        this(properties, objectMapper, fallback, relogio,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(validTimeout(properties)))
                        .build());
    }

    OpenAiBookingIntentInterpreter(OpenAiBookingIntentProperties properties,
                                   ObjectMapper objectMapper,
                                   HeuristicBookingIntentInterpreter fallback,
                                   RelogioDoNegocio relogio,
                                   HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.relogio = relogio;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<BookingIntent> interpretar(String mensagem) {
        if (!habilitada() || mensagem == null || mensagem.isBlank()) {
            return fallback.interpretar(mensagem);
        }

        try {
            AiInterpretation ai = interpretarComOpenAi(mensagem);
            return ai.completed()
                    ? ai.intent()
                    : fallback.interpretar(mensagem);
        } catch (Exception error) {
            log.warn("AI booking intent fallback activated (type={}).",
                    error.getClass().getSimpleName());
            return fallback.interpretar(mensagem);
        }
    }

    private AiInterpretation interpretarComOpenAi(String mensagem) throws Exception {
        String body = objectMapper.writeValueAsString(requestPayload(mensagem));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/responses"))
                .timeout(Duration.ofMillis(validTimeout(properties)))
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("OpenAI booking intent returned HTTP {}.", response.statusCode());
            return AiInterpretation.failed();
        }

        JsonNode envelope = objectMapper.readTree(response.body());
        String structured = extractOutputText(envelope).orElse(null);
        if (structured == null || structured.isBlank()) {
            return AiInterpretation.failed();
        }

        JsonNode parsed = objectMapper.readTree(structured);
        if (!parsed.path("booking_intent").asBoolean(false)) {
            return AiInterpretation.completed(Optional.empty());
        }

        String service = parsed.path("service_query").asText("").trim();
        String day = parsed.path("day_query").asText("").trim();
        LocalTime earliest = parseTime(parsed.path("earliest_time").asText(""));
        LocalTime latest = parseTime(parsed.path("latest_time").asText(""));
        LocalTime target = parseTime(parsed.path("target_time").asText(""));
        boolean sameAsUsual = parsed.path("same_as_usual").asBoolean(false);

        if (earliest != null && latest != null && latest.isBefore(earliest)) {
            return AiInterpretation.failed();
        }

        return AiInterpretation.completed(Optional.of(new BookingIntent(
                service,
                day,
                earliest,
                latest,
                target,
                sameAsUsual)));
    }

    private Map<String, Object> requestPayload(String mensagem) {
        String contextualPrompt = SYSTEM_PROMPT
                + "\nHoje no fuso do negócio é " + relogio.hoje() + ".";

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "booking_intent", Map.of("type", "boolean"),
                        "service_query", Map.of("type", "string"),
                        "day_query", Map.of("type", "string"),
                        "earliest_time", Map.of("type", "string"),
                        "latest_time", Map.of("type", "string"),
                        "target_time", Map.of("type", "string"),
                        "same_as_usual", Map.of("type", "boolean")
                ),
                "required", List.of(
                        "booking_intent",
                        "service_query",
                        "day_query",
                        "earliest_time",
                        "latest_time",
                        "target_time",
                        "same_as_usual"),
                "additionalProperties", false);

        return Map.of(
                "model", properties.getModel().trim(),
                "store", false,
                "reasoning", Map.of("effort", "none"),
                "max_output_tokens", 300,
                "input", List.of(
                        Map.of("role", "system", "content", contextualPrompt),
                        Map.of("role", "user", "content", mensagem)),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "booking_intent",
                                "strict", true,
                                "schema", schema)));
    }

    private Optional<String> extractOutputText(JsonNode envelope) {
        JsonNode output = envelope.path("output");
        if (!output.isArray()) {
            return Optional.empty();
        }

        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                if ("refusal".equals(part.path("type").asText())) {
                    return Optional.empty();
                }
                if ("output_text".equals(part.path("type").asText())) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        return Optional.of(text);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value.trim());
    }

    private boolean habilitada() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && properties.getModel() != null
                && !properties.getModel().isBlank();
    }

    private String baseUrl() {
        String value = properties.getBaseUrl() == null
                ? ""
                : properties.getBaseUrl().trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalStateException("OpenAI base URL is empty");
        }
        return value;
    }

    private static int validTimeout(OpenAiBookingIntentProperties properties) {
        return Math.max(250, properties.getTimeoutMs());
    }

    private record AiInterpretation(boolean completed, Optional<BookingIntent> intent) {
        static AiInterpretation completed(Optional<BookingIntent> intent) {
            return new AiInterpretation(true, intent == null ? Optional.empty() : intent);
        }

        static AiInterpretation failed() {
            return new AiInterpretation(false, Optional.empty());
        }
    }
}
