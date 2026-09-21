package com.troquim_bot.application.messaging;

import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.whatsapp.flow.application.session.FlowConfirmationOutcome;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStatus;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowCompletionProcessorTest {

    private static final String TOKEN = "flow-token-1";
    private static final String PHONE = "5511999990001";

    @Test
    void conclusaoUsaResultadoPersistidoLimpaConversaESoProduzEfeitoUmaVez() {
        FakeReceiptStore receipts = new FakeReceiptStore();
        FlowSessionStore sessions = mock(FlowSessionStore.class);
        BookingIdempotencyStore bookings = mock(BookingIdempotencyStore.class);
        ConversationStateService states = mock(ConversationStateService.class);

        FlowSession session = FlowSession.persistida(
                TOKEN, PHONE, UUID.randomUUID(), FlowSessionStatus.CONCLUIDA,
                LocalDateTime.now().minusMinutes(2), LocalDateTime.now().plusMinutes(20),
                Optional.of(new FlowConfirmationOutcome(
                        "Manicure", "2026-09-23", "10:00")));

        when(sessions.buscar(TOKEN)).thenReturn(Optional.of(session));

        FlowCompletionProcessor processor = new FlowCompletionProcessor(
                receipts, sessions, bookings, states);

        InboundFlowCompletion event = new InboundFlowCompletion(
                "whatsapp_cloud", "wamid.COMPLETE1", PHONE, TOKEN, 0L);

        ProcessOutcome first = processor.processOnce(event);
        assertTrue(first.processed());
        assertTrue(first.responseText().contains("Recebi seu agendamento"));
        assertTrue(first.responseText().contains("Manicure"));
        assertTrue(first.responseText().contains("23/09/2026"));
        assertTrue(first.responseText().contains("10h"));
        verify(states, times(1)).limparEstado(PHONE);
        verify(bookings, never()).buscarConfirmadoPorBase(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        // Receipt PENDING: uma reentrega pode reenviar a resposta, mas nao refaz efeitos.
        ProcessOutcome retry = processor.processOnce(event);
        assertTrue(retry.processed());
        assertEquals(first.responseText(), retry.responseText());
        verify(states, times(1)).limparEstado(PHONE);
    }

    @Test
    void tokenDesconhecidoNaoGeraConfirmacao() {
        FakeReceiptStore receipts = new FakeReceiptStore();
        FlowSessionStore sessions = mock(FlowSessionStore.class);
        BookingIdempotencyStore bookings = mock(BookingIdempotencyStore.class);
        ConversationStateService states = mock(ConversationStateService.class);

        when(sessions.buscar(TOKEN)).thenReturn(Optional.empty());

        FlowCompletionProcessor processor = new FlowCompletionProcessor(
                receipts, sessions, bookings, states);

        ProcessOutcome result = processor.processOnce(new InboundFlowCompletion(
                "whatsapp_cloud", "wamid.UNKNOWN", PHONE, TOKEN, 0L));

        assertTrue(result.processed());
        assertTrue(result.responseText() == null || result.responseText().isBlank());
        verify(states, never()).limparEstado(PHONE);
    }

    private static final class FakeReceiptStore implements InboundReceiptStore {
        private final Map<String, StoredReceipt> values = new HashMap<>();

        private String key(String provider, String id) {
            return provider + "|" + id;
        }

        @Override
        public Optional<StoredReceipt> find(String provider, String externalMessageId) {
            return Optional.ofNullable(values.get(key(provider, externalMessageId)));
        }

        @Override
        public void claimPending(String provider, String externalMessageId) {
            values.put(key(provider, externalMessageId), new StoredReceipt(STATUS_PENDING, null));
        }

        @Override
        public void completeProcessing(String provider, String externalMessageId, String responseText) {
            values.put(key(provider, externalMessageId),
                    new StoredReceipt(STATUS_PENDING, responseText));
        }

        @Override
        public void markSent(String provider, String externalMessageId, String outboundMessageId) {
            StoredReceipt current = values.get(key(provider, externalMessageId));
            values.put(key(provider, externalMessageId),
                    new StoredReceipt(STATUS_SENT, current == null ? null : current.responseText()));
        }
    }
}
