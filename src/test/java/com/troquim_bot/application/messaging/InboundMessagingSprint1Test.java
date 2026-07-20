package com.troquim_bot.application.messaging;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprova as três correções da Sprint 1, exercitando a orquestração REAL
 * ({@link InboundMessageIngestionService} + {@link InboundReceiptProcessor}) com duplos
 * de teste apenas para os colaboradores (portas + a conversa). Sem Spring/Docker.
 */
@DisplayName("Sprint 1 - correcoes de race, catch estreito e retry de outbound")
class InboundMessagingSprint1Test {

    private static final String PHONE = "5511999990000";

    // ==================== Bug 1: serialização por telefone ====================

    @Test
    @DisplayName("1. mensagens do mesmo telefone sao serializadas (sem read-modify-write concorrente)")
    void mensagensDoMesmoTelefoneSaoSerializadas() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        ConversationApplicationService conv = mock(ConversationApplicationService.class);
        when(conv.processarMensagem(anyString(), anyString())).thenAnswer(inv -> {
            int c = inFlight.incrementAndGet();
            maxConcurrent.accumulateAndGet(c, Math::max);
            Thread.sleep(200);   // alarga a janela para tornar a corrida observável
            inFlight.decrementAndGet();
            return "resposta";
        });

        RecordingGateway gateway = new RecordingGateway();
        InboundMessageIngestionService ingestion = ingestion(new FakeStore(), conv, gateway);

        // Dois telefones IGUAIS, message ids DIFERENTES (A, B) → devem serializar.
        Thread t1 = new Thread(() -> ingestion.ingest("A".getBytes(StandardCharsets.UTF_8), "sig"));
        Thread t2 = new Thread(() -> ingestion.ingest("B".getBytes(StandardCharsets.UTF_8), "sig"));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(1, maxConcurrent.get(),
                "duas mensagens do mesmo telefone nao podem avancar a conversa simultaneamente");
    }

    // ==================== Bug 2: catch estreito ====================

    @Test
    @DisplayName("2a. conflito UNIQUE do receipt vira duplicata (200), sem chamar conversa/outbound")
    void conflitoUniqueDoReceiptEhTratadoComoDuplicata() {
        ConversationApplicationService conv = mock(ConversationApplicationService.class);
        FakeStore store = new FakeStore();
        store.claimException = receiptUniqueViolation();   // SQLState 23505 + constraint do receipt
        RecordingGateway gateway = new RecordingGateway();

        IngestOutcome outcome = ingestion(store, conv, gateway)
                .ingest("x".getBytes(StandardCharsets.UTF_8), "sig");

        assertEquals(IngestOutcome.ACCEPTED, outcome, "duplicata concorrente → 200");
        verify(conv, never()).processarMensagem(anyString(), anyString());
        assertEquals(0, gateway.count(), "outbound nao deve ser chamado em duplicata");
    }

    @Test
    @DisplayName("2c. OUTRA violacao de integridade no claim PROPAGA (nao vira duplicata)")
    void outraViolacaoDeIntegridadeNoClaimPropaga() {
        ConversationApplicationService conv = mock(ConversationApplicationService.class);
        FakeStore store = new FakeStore();
        store.claimException = otherIntegrityViolation();   // 23502 (not-null), NAO e' o conflito de claim
        RecordingGateway gateway = new RecordingGateway();

        InboundMessageIngestionService ingestion = ingestion(store, conv, gateway);

        assertThrows(DataIntegrityViolationException.class,
                () -> ingestion.ingest("x".getBytes(StandardCharsets.UTF_8), "sig"));
        verify(conv, never()).processarMensagem(anyString(), anyString());
        assertEquals(0, gateway.count());
    }

    @Test
    @DisplayName("2b. DataIntegrityViolationException do dominio PROPAGA (nao e' engolida como duplicata)")
    void divDoDominioPropaga() {
        ConversationApplicationService conv = mock(ConversationApplicationService.class);
        when(conv.processarMensagem(anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("constraint de dominio (ex: Customer unico)"));
        RecordingGateway gateway = new RecordingGateway();

        InboundMessageIngestionService ingestion = ingestion(new FakeStore(), conv, gateway);

        assertThrows(DataIntegrityViolationException.class,
                () -> ingestion.ingest("y".getBytes(StandardCharsets.UTF_8), "sig"));
        assertEquals(0, gateway.count(), "sem envio quando a conversa falha");
    }

    // ==================== Bug 3: outbound falho → não-2xx ====================

    @Test
    @DisplayName("3. falha de outbound retorna OUTBOUND_UNAVAILABLE (nao-2xx → Meta reentrega)")
    void falhaDeOutboundRetornaNao2xx() {
        ConversationApplicationService conv = mock(ConversationApplicationService.class);
        when(conv.processarMensagem(anyString(), anyString())).thenReturn("resposta");
        RecordingGateway gateway = new RecordingGateway();
        gateway.failNext = true;   // sendText lança

        IngestOutcome outcome = ingestion(new FakeStore(), conv, gateway)
                .ingest("z".getBytes(StandardCharsets.UTF_8), "sig");

        assertEquals(IngestOutcome.OUTBOUND_UNAVAILABLE, outcome);
    }

    // ==================== infra do teste ====================

    /** Assinatura sempre válida + parser que devolve UMA mensagem com id = corpo, telefone fixo. */
    private InboundMessageIngestionService ingestion(FakeStore store,
                                                     ConversationApplicationService conv,
                                                     RecordingGateway gateway) {
        InboundReceiptProcessor processor = new InboundReceiptProcessor(store, conv);
        WebhookSignatureVerifier verifier = (body, sig) -> true;
        InboundMessageParser parser = raw -> new ParsedInboundPayload(true, List.of(
                new InboundTextMessage("whatsapp_cloud", new String(raw, StandardCharsets.UTF_8),
                        PHONE, "texto", 0L)));
        return new InboundMessageIngestionService(verifier, parser, processor, gateway);
    }

    /** Constrói a violação REAL da UNIQUE do receipt (SQLState 23505 + nome da constraint). */
    private static DataIntegrityViolationException receiptUniqueViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException(
                        "ERROR: duplicate key value violates unique constraint "
                                + "\"uq_inbound_receipt_provider_external_id\"",
                        "23505"));
    }

    /** Outra violação de integridade (not-null, 23502) — NÃO é o conflito de claim. */
    private static DataIntegrityViolationException otherIntegrityViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException("ERROR: null value in column violates not-null constraint", "23502"));
    }

    /** Store fake controlável: find sempre vazio (caminho de mensagem nova); claim opcionalmente falha. */
    static class FakeStore implements InboundReceiptStore {
        volatile RuntimeException claimException = null;

        @Override
        public Optional<StoredReceipt> find(String provider, String externalMessageId) {
            return Optional.empty();
        }

        @Override
        public void claimPending(String provider, String externalMessageId) {
            if (claimException != null) {
                throw claimException;
            }
        }

        @Override
        public void completeProcessing(String provider, String externalMessageId, String responseText) {
        }

        @Override
        public void markSent(String provider, String externalMessageId, String outboundMessageId) {
        }
    }

    /** Gateway fake: grava envios; opcionalmente lança para simular falha de outbound. */
    static class RecordingGateway implements OutboundMessageGateway {
        volatile boolean failNext = false;
        final List<String> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public OutboundResult sendText(String toPhone, String text) {
            if (failNext) {
                throw new RuntimeException("outbound indisponivel (simulado)");
            }
            sent.add(text);
            return new OutboundResult("wamid.out." + sent.size(), "sent");
        }

        int count() {
            return sent.size();
        }
    }
}
