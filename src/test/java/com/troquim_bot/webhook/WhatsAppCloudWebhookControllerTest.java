package com.troquim_bot.webhook;

import com.troquim_bot.application.messaging.OutboundMessageGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import com.troquim_bot.infrastructure.whatsappcloud.SpringDataInboundMessageReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integração do webhook WhatsApp Cloud pela SecurityFilterChain REAL (MockMvc),
 * profile test. Cobre verificação GET, assinatura HMAC do POST sobre bytes brutos,
 * cenários de payload, e a segurança das rotas exatas vs vizinhas. O outbound é um
 * duplo que grava chamadas (aqui o que se valida é o webhook, não o cliente HTTP).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Segredos FICTÍCIOS injetados só aqui (não em application*.properties).
        "troquim.integrations.whatsapp.cloud.verify-token=test-verify-token",
        "troquim.integrations.whatsapp.cloud.app-secret=test-app-secret",
        "troquim.integrations.whatsapp.cloud.access-token=test-access-token"
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Webhook WhatsApp Cloud - cadeia real")
class WhatsAppCloudWebhookControllerTest {

    private static final String CLOUD = "/webhook/whatsapp/cloud";
    private static final String SIG_HEADER = "X-Hub-Signature-256";
    private static final String VERIFY_TOKEN = "test-verify-token";   // = application-test.properties
    private static final String APP_SECRET = "test-app-secret";       // = application-test.properties
    private static final String ADMIN_TOKEN = "test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingOutboundGateway outbound;

    @Autowired
    private SpringDataInboundMessageReceiptRepository receipts;

    @BeforeEach
    void reset() {
        outbound.sent.clear();
        outbound.failNext.set(false);
    }

    // ==================== GET verificação (1-4) ====================

    @Test
    @DisplayName("1. token correto retorna o challenge")
    void verificacaoTokenCorretoRetornaChallenge() throws Exception {
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "1234567890"))
                .andExpect(status().isOk())
                .andExpect(content().string("1234567890"));
    }

    @Test
    @DisplayName("2. token incorreto retorna 403")
    void verificacaoTokenIncorretoRetorna403() throws Exception {
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado")
                        .param("hub.challenge", "1234567890"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. parametros ausentes retornam 400")
    void verificacaoParametrosAusentesRetorna400() throws Exception {
        mockMvc.perform(get(CLOUD).param("hub.mode", "subscribe"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. verify token nao aparece em logs")
    void verifyTokenNaoApareceEmLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "abc"))
                .andExpect(status().isOk());
        assertFalse(output.getAll().contains(VERIFY_TOKEN), "verify token nao pode ser logado");
    }

    // ==================== POST assinatura (5-9) ====================

    @Test
    @DisplayName("5/9. assinatura valida sobre bytes brutos e' aceita e chama Application")
    void assinaturaValidaAceita() throws Exception {
        byte[] body = textPayload("wamid.A1", "5511999990001", "oi").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(1, outbound.sent.size(), "payload de texto valido deve chamar o fluxo (outbound)");
    }

    @Test
    @DisplayName("6. assinatura invalida retorna 401")
    void assinaturaInvalidaRetorna401() throws Exception {
        byte[] body = textPayload("wamid.A2", "5511999990001", "oi").getBytes(StandardCharsets.UTF_8);
        postSigned(body, "sha256=deadbeef").andExpect(status().isUnauthorized());
        assertEquals(0, outbound.sent.size());
    }

    @Test
    @DisplayName("7. assinatura ausente retorna 401")
    void assinaturaAusenteRetorna401() throws Exception {
        byte[] body = textPayload("wamid.A3", "5511999990001", "oi").getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post(CLOUD).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertEquals(0, outbound.sent.size());
    }

    @Test
    @DisplayName("8. body alterado invalida a assinatura (401)")
    void bodyAlteradoInvalidaAssinatura() throws Exception {
        byte[] original = textPayload("wamid.A4", "5511999990001", "oi").getBytes(StandardCharsets.UTF_8);
        String signature = sign(original, APP_SECRET);
        byte[] tampered = textPayload("wamid.A4", "5511999990001", "TAMPERED").getBytes(StandardCharsets.UTF_8);
        postSigned(tampered, signature).andExpect(status().isUnauthorized());
        assertEquals(0, outbound.sent.size());
    }

    // ==================== POST payload (10-15) ====================

    @Test
    @DisplayName("10. payload de texto valido chama Application (outbound acionado)")
    void payloadTextoChamaApplication() throws Exception {
        byte[] body = textPayload("wamid.B1", "5511999990002", "quero agendar").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(1, outbound.sent.size());
    }

    @Test
    @DisplayName("11. status-only retorna 200 sem chamar Conversation")
    void statusOnlyNaoChamaConversation() throws Exception {
        byte[] body = statusPayload("wamid.B2").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(0, outbound.sent.size());
    }

    @Test
    @DisplayName("12. payload sem mensagens retorna 200")
    void payloadSemMensagensRetorna200() throws Exception {
        byte[] body = emptyValuePayload().getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(0, outbound.sent.size());
    }

    @Test
    @DisplayName("13. multiplas mensagens sao tratadas")
    void multiplasMensagens() throws Exception {
        byte[] body = twoTextMessages("wamid.C1", "wamid.C2", "5511999990003").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(2, outbound.sent.size());
    }

    @Test
    @DisplayName("14. tipo nao suportado nao cria acao de negocio")
    void tipoNaoSuportadoNaoCriaAcao() throws Exception {
        byte[] body = imagePayload("wamid.C3", "5511999990004").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
        assertEquals(0, outbound.sent.size());
    }

    @Test
    @DisplayName("phone_number_id divergente do piloto: 200, sem receipt, sem Conversation, sem outbound")
    void phoneNumberIdDivergenteNaoProcessa() throws Exception {
        byte[] body = textPayloadWithPhoneId("wamid.PNID1", "5511999990006", "oi", "outro-phone-id")
                .getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());   // mismatch → 200
        assertEquals(0, outbound.sent.size(), "mismatch de phone_number_id nao pode acionar o outbound");
        // não criar receipt (se a Conversation/claim tivesse rodado, existiria um receipt):
        assertFalse(receipts.existsByProviderAndExternalMessageId("whatsapp_cloud", "wamid.PNID1"),
                "mismatch de phone_number_id nao pode criar receipt (nem chamar Conversation)");
    }

    @Test
    @DisplayName("15. payload malformado retorna 400 sem corpo sensivel")
    void payloadMalformadoRetorna400() throws Exception {
        byte[] body = "{ not valid json ".getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    // ==================== Segurança (16-19) ====================

    @Test
    @DisplayName("16. GET/POST exatos do cloud atravessam o Security (nao 401 por auth)")
    void rotasExatasAtravessamSecurity() throws Exception {
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "ok"))
                .andExpect(status().isOk());
        byte[] body = textPayload("wamid.D1", "5511999990005", "oi").getBytes(StandardCharsets.UTF_8);
        postSigned(body, sign(body, APP_SECRET)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("17. rota vizinha permanece bloqueada (401)")
    void rotaVizinhaBloqueada() throws Exception {
        mockMvc.perform(get(CLOUD + "/qualquer-coisa")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CLOUD + "/qualquer-coisa")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("18. endpoints administrativos permanecem protegidos (401 sem token)")
    void administrativosProtegidos() throws Exception {
        mockMvc.perform(get("/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("19. health permanece publico")
    void healthPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ==================== Bug 3: outbound falho → 503 (Meta reentrega) → reenvio ====================

    @Test
    @DisplayName("outbound falho retorna 503 (nao-2xx); a re-entrega reenvia e retorna 200")
    void outboundFalho503EReentregaReenvia() throws Exception {
        byte[] body = textPayload("wamid.RETRY1", "5511999990099", "oi").getBytes(StandardCharsets.UTF_8);
        String signature = sign(body, APP_SECRET);

        // 1ª entrega: o envio falha → 503 (a Meta so reentrega em nao-2xx). Nada foi enviado.
        outbound.failNext.set(true);
        postSigned(body, signature).andExpect(status().isServiceUnavailable());
        assertEquals(0, outbound.sent.size());

        // Re-entrega (a Meta reenvia porque recebeu 503): agora o outbound funciona →
        // reenvia a resposta persistida, sem reprocessar a conversa, e retorna 200.
        outbound.failNext.set(false);
        postSigned(body, signature).andExpect(status().isOk());
        assertEquals(1, outbound.sent.size(), "a resposta foi reenviada na re-entrega");
    }

    // ==================== helpers ====================

    private org.springframework.test.web.servlet.ResultActions postSigned(byte[] body, String signature)
            throws Exception {
        return mockMvc.perform(post(CLOUD)
                .header(SIG_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] h = mac.doFinal(body);
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : h) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String textPayload(String id, String from, String text) {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
            "contacts":[{"wa_id":"%s"}],
            "messages":[{"id":"%s","from":"%s","timestamp":"1700000000","type":"text","text":{"body":"%s"}}]}}]}]}
            """.formatted(from, id, from, text);
    }

    private static String textPayloadWithPhoneId(String id, String from, String text, String phoneNumberId) {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"%s"},
            "messages":[{"id":"%s","from":"%s","timestamp":"1700000000","type":"text","text":{"body":"%s"}}]}}]}]}
            """.formatted(phoneNumberId, id, from, text);
    }

    private static String twoTextMessages(String id1, String id2, String from) {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
            "messages":[
              {"id":"%s","from":"%s","timestamp":"1700000000","type":"text","text":{"body":"oi"}},
              {"id":"%s","from":"%s","timestamp":"1700000001","type":"text","text":{"body":"ola"}}
            ]}}]}]}
            """.formatted(id1, from, id2, from);
    }

    private static String statusPayload(String id) {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
            "statuses":[{"id":"%s","status":"delivered","timestamp":"1700000000","recipient_id":"5511999990001"}]}}]}]}
            """.formatted(id);
    }

    private static String emptyValuePayload() {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"}}}]}]}
            """;
    }

    private static String imagePayload(String id, String from) {
        return """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
            "messages":[{"id":"%s","from":"%s","timestamp":"1700000000","type":"image","image":{"id":"MEDIA"}}]}}]}]}
            """.formatted(id, from);
    }

    /** Duplo que grava os envios outbound (o que se valida aqui e' o webhook). */
    static class RecordingOutboundGateway implements OutboundMessageGateway {
        final List<String> sent = new ArrayList<>();
        final AtomicBoolean failNext = new AtomicBoolean(false);

        @Override
        public synchronized OutboundResult sendText(String toPhone, String text) {
            if (failNext.get()) {
                throw new RuntimeException("outbound indisponivel (simulado)");
            }
            sent.add(toPhone + "|" + text);
            return new OutboundResult("wamid.OUT." + sent.size(), "sent");
        }
    }

    @TestConfiguration
    static class OutboundStubConfig {
        @Bean
        @Primary
        RecordingOutboundGateway recordingOutboundGateway() {
            return new RecordingOutboundGateway();
        }
    }
}
