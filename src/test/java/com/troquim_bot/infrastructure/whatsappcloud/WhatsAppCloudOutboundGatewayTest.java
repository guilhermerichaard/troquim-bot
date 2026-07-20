package com.troquim_bot.infrastructure.whatsappcloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.troquim_bot.application.messaging.OutboundResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o client HTTP outbound REAL contra um servidor fake (JDK HttpServer),
 * sem mocks do gateway. Cobre: URL correta, Authorization Bearer, payload de texto,
 * captura do id outbound em 2xx, erro tipado em 4xx/5xx, tratamento de timeout e
 * ausência de segredo/Authorization nos logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class WhatsAppCloudOutboundGatewayTest {

    private static final String ACCESS_TOKEN = "secret-access-token-nao-deve-vazar";
    private static final String PHONE_NUMBER_ID = "111222333";
    private static final String GRAPH_VERSION = "vtest";

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    // Captura da última requisição recebida pelo fake.
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    // Resposta configurável do fake.
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"messages\":[{\"id\":\"wamid.OUT123\"}]}";
    private volatile long delayMillis = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private WhatsAppCloudOutboundGateway gateway(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(factory)
                .build();

        WhatsAppCloudProperties props = new WhatsAppCloudProperties();
        props.setGraphApiVersion(GRAPH_VERSION);
        props.setPhoneNumberId(PHONE_NUMBER_ID);
        props.setAccessToken(ACCESS_TOKEN);
        return new WhatsAppCloudOutboundGateway(restClient, props);
    }

    @Test
    void sucessoEnviaUrlAuthPayloadCorretosECapturaIdOutbound() throws Exception {
        OutboundResult result = gateway(Duration.ofSeconds(5)).sendText("5511999990000", "Olá do TroQuim");

        // 25. URL correta: /{version}/{phone-number-id}/messages
        assertEquals("POST", lastMethod.get());
        assertEquals("/" + GRAPH_VERSION + "/" + PHONE_NUMBER_ID + "/messages", lastPath.get());
        // 26. Authorization Bearer correto
        assertEquals("Bearer " + ACCESS_TOKEN, lastAuth.get());
        // 27. Payload de texto correto
        JsonNode body = mapper.readTree(lastBody.get());
        assertEquals("whatsapp", body.path("messaging_product").asText());
        assertEquals("individual", body.path("recipient_type").asText());
        assertEquals("5511999990000", body.path("to").asText());
        assertEquals("text", body.path("type").asText());
        assertFalse(body.path("text").path("preview_url").asBoolean());
        assertEquals("Olá do TroQuim", body.path("text").path("body").asText());
        // 28. Graph version e phone id vêm da configuração (refletidos na URL acima)
        // 29. 2xx captura external outbound id
        assertEquals("wamid.OUT123", result.externalMessageId());
        assertEquals("sent", result.status());
    }

    @Test
    void erro4xxViraExcecaoTipada() {
        responseStatus = 400;
        responseBody = "{\"error\":{\"message\":\"bad\"}}";
        WhatsAppCloudApiException ex = assertThrows(WhatsAppCloudApiException.class,
                () -> gateway(Duration.ofSeconds(5)).sendText("5511999990000", "x"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void erro5xxViraExcecaoTipada() {
        responseStatus = 503;
        responseBody = "{\"error\":{\"message\":\"unavailable\"}}";
        WhatsAppCloudApiException ex = assertThrows(WhatsAppCloudApiException.class,
                () -> gateway(Duration.ofSeconds(5)).sendText("5511999990000", "x"));
        assertEquals(503, ex.getStatusCode());
    }

    @Test
    void timeoutEhTratadoComoExcecaoTipada() {
        delayMillis = 800;
        WhatsAppCloudApiException ex = assertThrows(WhatsAppCloudApiException.class,
                () -> gateway(Duration.ofMillis(200)).sendText("5511999990000", "x"));
        assertNotNull(ex);
    }

    @Test
    void segredoNaoApareceNosLogs(CapturedOutput output) {
        gateway(Duration.ofSeconds(5)).sendText("5511999990000", "conteudo pessoal do cliente");
        assertFalse(output.getAll().contains(ACCESS_TOKEN), "Access token nao pode aparecer em log");
        assertFalse(output.getAll().contains("Bearer " + ACCESS_TOKEN), "Authorization nao pode aparecer em log");
        assertTrue(true);
    }
}
