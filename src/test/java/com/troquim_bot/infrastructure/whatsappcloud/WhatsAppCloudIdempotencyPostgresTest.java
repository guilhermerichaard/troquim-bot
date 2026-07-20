package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.InboundMessageIngestionService;
import com.troquim_bot.application.messaging.IngestOutcome;
import com.troquim_bot.application.messaging.OutboundMessageGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Idempotência durável e limite transacional no PostgreSQL REAL (Testcontainers),
 * profile azure (Flyway aplica a V3). Sem mocks das partes validadas: receipt store
 * e InboundReceiptProcessor reais. O outbound é um duplo (aqui valida-se a dedup, não
 * o cliente HTTP). Cobre idempotência (20-24) e o fluxo/limite transacional (37-40).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("WhatsApp Cloud - idempotencia duravel (PostgreSQL)")
class WhatsAppCloudIdempotencyPostgresTest {

    private static final String APP_SECRET = "postgres-test-app-secret";
    private static final String PILOT = "11111111-1111-1111-1111-111111111111";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> PILOT);
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
        // Integração ligada com credenciais ficticias (sem whitespace) → azure valida OK.
        registry.add("troquim.integrations.whatsapp.cloud.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.cloud.verify-token", () -> "pg-verify-token");
        registry.add("troquim.integrations.whatsapp.cloud.app-secret", () -> APP_SECRET);
        registry.add("troquim.integrations.whatsapp.cloud.access-token", () -> "pg-access-token");
        registry.add("troquim.integrations.whatsapp.cloud.phone-number-id", () -> "pg-phone-id");
        registry.add("troquim.integrations.whatsapp.cloud.graph-api-version", () -> "vtest");
        registry.add("troquim.integrations.whatsapp.cloud.base-url", () -> "http://localhost:59999");
    }

    @Autowired
    private InboundMessageIngestionService ingestionService;
    @Autowired
    private SpringDataInboundMessageReceiptRepository receipts;
    @Autowired
    private RecordingOutboundGateway outbound;

    @BeforeEach
    void reset() {
        receipts.deleteAll();
        outbound.reset();
    }

    // ==================== 20/21/38 — dedup por message id ====================

    @Test
    @DisplayName("20/21/38. mesma message ID processa uma vez; duplicata e' aceita e nao reprocessa")
    void mesmaMensagemProcessaUmaVez() {
        byte[] body = textPayload("wamid.DUP1", "5511999990001", "quero agendar");
        String sig = sign(body);

        assertEquals(IngestOutcome.ACCEPTED, ingestionService.ingest(body, sig));
        assertEquals(IngestOutcome.ACCEPTED, ingestionService.ingest(body, sig), "duplicata retorna ACCEPTED (200)");

        assertEquals(1, receipts.count(), "apenas um receipt durável");
        assertEquals(1, outbound.count(), "conversa/outbound acionados uma unica vez");
    }

    // ==================== 22 — concorrência ====================

    @Test
    @DisplayName("22. concorrencia com a mesma message ID nao duplica")
    void concorrenciaNaoDuplica() throws Exception {
        byte[] body = textPayload("wamid.CONC1", "5511999990002", "oi");
        String sig = sign(body);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                ingestionService.ingest(body, sig);
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, receipts.count(), "somente um receipt apesar da concorrencia");
        assertEquals(1, outbound.count(), "conversa/outbound uma unica vez sob concorrencia");
    }

    // ==================== 23 — durabilidade (persistência) ====================

    @Test
    @DisplayName("23. idempotencia e' duravel (persistida no banco, sem estado em memoria)")
    void idempotenciaDuravelNoBanco() {
        byte[] body = textPayload("wamid.DUR1", "5511999990003", "oi");
        String sig = sign(body);
        ingestionService.ingest(body, sig);

        // O receipt está comitado no banco — a dedup não depende de memória do processo.
        assertTrue(receipts.existsByProviderAndExternalMessageId("whatsapp_cloud", "wamid.DUR1"));
        assertEquals(1, receipts.count());

        // Nova entrega (como após um reinício) lê o estado do banco e não reprocessa.
        ingestionService.ingest(body, sig);
        assertEquals(1, outbound.count());
    }

    // ==================== 24 — constraint PostgreSQL ====================

    @Test
    @DisplayName("24. UNIQUE(provider, external_message_id) e' aplicada pelo PostgreSQL")
    void constraintUniqueAplicada() {
        LocalDateTime now = LocalDateTime.now();
        receipts.saveAndFlush(new InboundMessageReceiptJpaEntity(
                UUID.randomUUID(), "whatsapp_cloud", "wamid.UQ1", "PROCESSED", now, now));

        assertThrows(DataIntegrityViolationException.class, () ->
                receipts.saveAndFlush(new InboundMessageReceiptJpaEntity(
                        UUID.randomUUID(), "whatsapp_cloud", "wamid.UQ1", "PROCESSED", now, now)));
    }

    // ==================== 37/40 — fluxo inbound → Conversation → outbound ====================

    @Test
    @DisplayName("37/40. inbound aciona o fluxo real de Conversation e produz resposta outbound")
    void fluxoInboundConversationOutbound() {
        byte[] body = textPayload("wamid.FLOW1", "5511999990004", "oi");
        ingestionService.ingest(body, sign(body));

        assertEquals(1, outbound.count());
        String reply = outbound.sent.get(0);
        assertFalse(reply.isBlank(), "a resposta veio do fluxo real de Conversation (nao vazia)");
    }

    // ========= 39/retry — outbound falha: PENDING com resposta; re-entrega reenvia sem reprocessar =========

    @Test
    @DisplayName("39. outbound falha -> PENDING com resposta; re-entrega reenvia sem reprocessar; vira SENT")
    void falhaOutboundReenviaSemReprocessar() {
        outbound.failNext.set(true);
        byte[] body = textPayload("wamid.FAIL1", "5511999990005", "oi");
        String sig = sign(body);

        // 1) Primeira chamada processa o negócio; o envio outbound falha.
        ingestionService.ingest(body, sig);
        InboundMessageReceiptJpaEntity afterFirst = receipts
                .findByProviderAndExternalMessageId("whatsapp_cloud", "wamid.FAIL1").orElseThrow();
        assertEquals(1, receipts.count());
        assertEquals("PENDING", afterFirst.getStatus(), "outbound falhou → receipt fica PENDING");
        assertFalse(afterFirst.getResponseText() == null || afterFirst.getResponseText().isBlank(),
                "a resposta foi persistida para permitir retry");
        assertEquals(0, outbound.count(), "nenhum envio bem-sucedido ainda");
        String persisted = afterFirst.getResponseText();

        // 2) Re-entrega da MESMA message ID: não chama Conversation de novo; reenvia a resposta
        //    persistida; ao ter sucesso, vira SENT.
        outbound.failNext.set(false);
        ingestionService.ingest(body, sig);
        assertEquals(1, receipts.count(), "nenhuma acao de negocio adicional (sem duplicar)");
        assertEquals(1, outbound.count(), "a resposta foi reenviada exatamente uma vez");
        assertEquals(persisted, outbound.sent.get(0),
                "reenviou a resposta PERSISTIDA (prova de que a Conversation nao rodou de novo)");
        InboundMessageReceiptJpaEntity afterRetry = receipts
                .findByProviderAndExternalMessageId("whatsapp_cloud", "wamid.FAIL1").orElseThrow();
        assertEquals("SENT", afterRetry.getStatus(), "apos sucesso do outbound → SENT");
    }

    // ==================== helpers ====================

    private static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(body);
            StringBuilder sb = new StringBuilder("sha256=");
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] textPayload(String id, String from, String text) {
        return ("""
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
            "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"pg-phone-id"},
            "messages":[{"id":"%s","from":"%s","timestamp":"1700000000","type":"text","text":{"body":"%s"}}]}}]}]}
            """).formatted(id, from, text).getBytes(StandardCharsets.UTF_8);
    }

    static class RecordingOutboundGateway implements OutboundMessageGateway {
        final List<String> sent = new ArrayList<>();
        final AtomicBoolean failNext = new AtomicBoolean(false);

        @Override
        public synchronized OutboundResult sendText(String toPhone, String text) {
            if (failNext.get()) {
                throw new RuntimeException("falha simulada de envio outbound");
            }
            sent.add(text);
            return new OutboundResult("wamid.OUT." + sent.size(), "sent");
        }

        synchronized int count() {
            return sent.size();
        }

        synchronized int successCount() {
            return sent.size();
        }

        synchronized void reset() {
            sent.clear();
            failNext.set(false);
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
