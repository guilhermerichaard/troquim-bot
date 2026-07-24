package com.troquim_bot.infrastructure.whatsappcloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Envio da mensagem interativa de Flow contra um servidor fake (JDK HttpServer) — o
 * client HTTP é o real, só o outro lado é falso.
 *
 * O que importa aqui é o CONTRATO com a Meta: um campo errado em
 * {@code interactive.action.parameters} faz a Meta rejeitar a mensagem, e nenhum teste
 * de camada superior perceberia.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Gateway de mensagem de Flow (Cloud API)")
class WhatsAppCloudFlowGatewayTest {

    private static final String ACCESS_TOKEN = "secret-access-token-nao-deve-vazar";
    private static final String PHONE_NUMBER_ID = "111222333";
    private static final String GRAPH_VERSION = "vtest";
    private static final String FLOW_TOKEN = "token-secreto-da-sessao-nao-deve-vazar";

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"messages\":[{\"id\":\"wamid.FLOW1\"}]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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

    @Test
    @DisplayName("12. envia o payload interativo de Flow no formato da Cloud API")
    void payloadInterativoCorreto() throws Exception {
        OutboundResult resultado = gateway().sendFlow("5511999990000", mensagem());

        assertEquals("/" + GRAPH_VERSION + "/" + PHONE_NUMBER_ID + "/messages", lastPath.get());
        assertEquals("Bearer " + ACCESS_TOKEN, lastAuth.get());

        JsonNode body = mapper.readTree(lastBody.get());
        assertEquals("whatsapp", body.path("messaging_product").asText());
        assertEquals("5511999990000", body.path("to").asText());
        assertEquals("interactive", body.path("type").asText());

        JsonNode interactive = body.path("interactive");
        assertEquals("flow", interactive.path("type").asText());
        assertFalse(interactive.path("body").path("text").asText().isBlank());

        JsonNode action = interactive.path("action");
        assertEquals("flow", action.path("name").asText());

        JsonNode params = action.path("parameters");
        assertEquals("3", params.path("flow_message_version").asText());
        assertEquals(FLOW_TOKEN, params.path("flow_token").asText());
        assertEquals("1234567890", params.path("flow_id").asText());
        assertEquals("Abrir agenda", params.path("flow_cta").asText());
        // Flow com Data Endpoint: a primeira tela vem do INIT, não de um payload fixo.
        assertEquals("data_exchange", params.path("flow_action").asText());
        assertEquals("published", params.path("mode").asText());
        assertTrue(params.path("flow_action_payload").isMissingNode(),
                "Com data_exchange a mensagem não deve fixar tela nem dados iniciais");

        assertEquals("wamid.FLOW1", resultado.externalMessageId());
        assertEquals("sent", resultado.status());
    }

    @Test
    @DisplayName("modo rascunho é enviado como draft")
    void modoRascunho() throws Exception {
        gateway().sendFlow("5511999990000", new FlowMessage(
                "1234567890", null, FLOW_TOKEN, "Abrir agenda", "Vamos agendar?", true));

        JsonNode params = mapper.readTree(lastBody.get())
                .path("interactive").path("action").path("parameters");
        assertEquals("draft", params.path("mode").asText());
    }

    @Test
    @DisplayName("sem flow_id, usa flow_name")
    void usaFlowNameQuandoNaoHaId() throws Exception {
        gateway().sendFlow("5511999990000", new FlowMessage(
                null, "agendamento-salao", FLOW_TOKEN, "Abrir agenda", "Vamos agendar?", false));

        JsonNode params = mapper.readTree(lastBody.get())
                .path("interactive").path("action").path("parameters");
        assertEquals("agendamento-salao", params.path("flow_name").asText());
        assertTrue(params.path("flow_id").isMissingNode());
    }

    @Test
    @DisplayName("10. erro HTTP vira exceção tipada com o status preservado")
    void erroHttpViraExcecaoTipada() {
        responseStatus = 401;
        // Formato real do token expirado observado em operação (code 190 / subcode 463).
        responseBody = "{\"error\":{\"code\":190,\"error_subcode\":463,\"message\":\"Session expired\"}}";

        WhatsAppCloudApiException erro = assertThrows(WhatsAppCloudApiException.class,
                () -> gateway().sendFlow("5511999990000", mensagem()));

        assertNotNull(erro.getMessage());
        assertTrue(erro.getMessage().contains("401"));
    }

    @Test
    @DisplayName("16. nem o access token nem o flow_token aparecem no log")
    void semVazamentoEmLog(CapturedOutput saida) {
        gateway().sendFlow("5511999990000", mensagem());

        String log = saida.getAll();
        assertFalse(log.contains(ACCESS_TOKEN), "Access token da Meta não pode aparecer em log");
        assertFalse(log.contains(FLOW_TOKEN), "flow_token não pode aparecer em log");
        assertFalse(log.contains("5511999990000"), "Telefone não pode aparecer em log");
    }

    @Test
    @DisplayName("o rótulo do botão respeita o limite de 20 caracteres da Meta")
    void ctaLimitado() {
        assertThrows(IllegalArgumentException.class, () -> new FlowMessage(
                "1", null, FLOW_TOKEN, "um rotulo absurdamente longo para um botao",
                "corpo", false));
    }

    private static FlowMessage mensagem() {
        return new FlowMessage("1234567890", null, FLOW_TOKEN, "Abrir agenda",
                "Posso te mostrar os horários livres?", false);
    }

    private WhatsAppCloudFlowGateway gateway() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(factory)
                .build();

        WhatsAppCloudProperties props = new WhatsAppCloudProperties();
        props.setGraphApiVersion(GRAPH_VERSION);
        props.setPhoneNumberId(PHONE_NUMBER_ID);
        props.setAccessToken(ACCESS_TOKEN);
        return new WhatsAppCloudFlowGateway(restClient, props);
    }
}
