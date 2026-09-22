package com.troquim_bot.infrastructure.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.conversation.BookingIntent;
import com.troquim_bot.conversation.HeuristicBookingIntentInterpreter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiBookingIntentInterpreterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void desligadoUsaHeuristicoSemChamarApi() {
        OpenAiBookingIntentProperties properties = new OpenAiBookingIntentProperties();
        properties.setEnabled(false);

        OpenAiBookingIntentInterpreter interpreter = new OpenAiBookingIntentInterpreter(
                properties,
                new ObjectMapper(),
                new HeuristicBookingIntentInterpreter(),
                RelogioDoNegocio.fixo(LocalDateTime.of(2026, 9, 22, 12, 0)));

        BookingIntent intent = interpreter
                .interpretar("quero manicure sexta depois das 16")
                .orElseThrow();

        assertEquals("sexta", intent.dayQuery());
        assertEquals("16:00", intent.earliestTime().toString());
    }

    @Test
    void respostaEstruturadaDaIaViraBookingIntentSemDecidirAgenda() throws Exception {
        startServer(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\"booking_intent\":true,\"service_query\":\"manicure\",\"day_query\":\"sexta\",\"earliest_time\":\"16:00\",\"latest_time\":\"\",\"target_time\":\"16:00\",\"same_as_usual\":false}"
                        }
                      ]
                    }
                  ]
                }
                """);

        OpenAiBookingIntentProperties properties = enabledProperties();

        OpenAiBookingIntentInterpreter interpreter = new OpenAiBookingIntentInterpreter(
                properties,
                new ObjectMapper(),
                new HeuristicBookingIntentInterpreter(),
                RelogioDoNegocio.fixo(LocalDateTime.of(2026, 9, 22, 12, 0)));

        BookingIntent intent = interpreter
                .interpretar("queria fazer a unha na sexta depois das quatro")
                .orElseThrow();

        assertEquals("manicure", intent.serviceQuery());
        assertEquals("sexta", intent.dayQuery());
        assertEquals("16:00", intent.earliestTime().toString());
        assertEquals("16:00", intent.targetTime().toString());
        assertTrue(!intent.sameAsUsual());
    }

    @Test
    void erroDaApiCaiNoHeuristicoAtual() throws Exception {
        startServer(500, "{\"error\":{\"message\":\"boom\"}}");

        OpenAiBookingIntentInterpreter interpreter = new OpenAiBookingIntentInterpreter(
                enabledProperties(),
                new ObjectMapper(),
                new HeuristicBookingIntentInterpreter(),
                RelogioDoNegocio.fixo(LocalDateTime.of(2026, 9, 22, 12, 0)));

        BookingIntent intent = interpreter
                .interpretar("quero manicure sexta depois das 16")
                .orElseThrow();

        assertEquals("sexta", intent.dayQuery());
        assertEquals("16:00", intent.earliestTime().toString());
    }

    @Test
    void mensagemNaoAgendamentoNaoViraIntentQuandoIaRecusa() throws Exception {
        startServer(200, """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\"booking_intent\":false,\"service_query\":\"\",\"day_query\":\"\",\"earliest_time\":\"\",\"latest_time\":\"\",\"target_time\":\"\",\"same_as_usual\":false}"
                        }
                      ]
                    }
                  ]
                }
                """);

        OpenAiBookingIntentInterpreter interpreter = new OpenAiBookingIntentInterpreter(
                enabledProperties(),
                new ObjectMapper(),
                new HeuristicBookingIntentInterpreter(),
                RelogioDoNegocio.fixo(LocalDateTime.of(2026, 9, 22, 12, 0)));

        assertTrue(interpreter.interpretar("oi, tudo bem?").isEmpty());
    }

    private OpenAiBookingIntentProperties enabledProperties() {
        OpenAiBookingIntentProperties properties = new OpenAiBookingIntentProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gpt-5.6-luna");
        properties.setTimeoutMs(1000);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }

    private void startServer(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
