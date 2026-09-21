package com.troquim_bot.application.messaging;

import com.troquim_bot.application.booking.BookingIdempotencyRecord;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.BookingIdempotencyOutcome;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.whatsapp.flow.application.session.FlowConfirmationOutcome;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Fecha o ciclo conversa -> Flow -> conversa.
 *
 * IA/canal interpreta apenas o evento. A verdade vem de estado previamente persistido:
 * FlowSession emitida pelo Troquim e, como fallback mais forte, booking_idempotency
 * commitado junto com o Appointment.
 *
 * O receipt de inbound torna a notificacao idempotente e permite retry somente do
 * outbound quando a Meta reentregar o mesmo nfm_reply.
 */
@Service
@ConditionalOnWhatsAppFlow
public class FlowCompletionProcessor {

    private static final String RECEIPT_UNIQUE_CONSTRAINT =
            "uq_inbound_receipt_provider_external_id";
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InboundReceiptStore receiptStore;
    private final FlowSessionStore sessionStore;
    private final BookingIdempotencyStore bookingStore;
    private final ConversationStateService conversationStateService;

    public FlowCompletionProcessor(InboundReceiptStore receiptStore,
                                   FlowSessionStore sessionStore,
                                   BookingIdempotencyStore bookingStore,
                                   ConversationStateService conversationStateService) {
        this.receiptStore = receiptStore;
        this.sessionStore = sessionStore;
        this.bookingStore = bookingStore;
        this.conversationStateService = conversationStateService;
    }

    @Transactional
    public void markSent(InboundFlowCompletion completion) {
        if (completion == null) {
            return;
        }
        receiptStore.markSent(
                completion.provider(), completion.externalMessageId(), null);
    }

    @Transactional
    public ProcessOutcome processOnce(InboundFlowCompletion completion) {
        var existing = receiptStore.find(completion.provider(), completion.externalMessageId());
        if (existing.isPresent()) {
            if (InboundReceiptStore.STATUS_SENT.equals(existing.get().status())) {
                return ProcessOutcome.duplicate();
            }
            return ProcessOutcome.processed(completion.fromPhone(), existing.get().responseText());
        }

        try {
            receiptStore.claimPending(completion.provider(), completion.externalMessageId());
        } catch (DataIntegrityViolationException integrityViolation) {
            if (isReceiptUniqueViolation(integrityViolation)) {
                throw new ConcurrentReceiptClaimException(
                        completion.provider(), completion.externalMessageId(), integrityViolation);
            }
            throw integrityViolation;
        }

        Optional<FlowSession> sessionOpt = sessionStore.buscar(completion.flowToken());
        if (sessionOpt.isEmpty()) {
            // Token desconhecido: evento nao e confiavel o bastante para produzir mensagem.
            receiptStore.completeProcessing(
                    completion.provider(), completion.externalMessageId(), null);
            return ProcessOutcome.processed(completion.fromPhone(), null);
        }

        FlowSession session = sessionOpt.get();
        if (session.preview() || session.telefone() == null
                || !session.telefone().equals(completion.fromPhone())) {
            receiptStore.completeProcessing(
                    completion.provider(), completion.externalMessageId(), null);
            return ProcessOutcome.processed(completion.fromPhone(), null);
        }

        Optional<FlowConfirmationOutcome> outcome = session.resultado();

        // A FlowSession e estado de apresentacao e pode falhar DEPOIS do booking ter
        // commitado. Neste caso reconstrui a apresentacao do recibo transacional.
        if (outcome.isEmpty() && session.businessId() != null) {
            outcome = bookingStore.buscarConfirmadoPorBase(
                            BusinessId.from(session.businessId()), completion.flowToken())
                    .filter(record -> record.status() == BookingIdempotencyOutcome.CONFIRMADO)
                    .map(FlowCompletionProcessor::outcomeFromBooking);
        }

        if (outcome.isEmpty()) {
            receiptStore.completeProcessing(
                    completion.provider(), completion.externalMessageId(), null);
            return ProcessOutcome.processed(completion.fromPhone(), null);
        }

        // O Flow terminou: o formulario textual antigo nao pode continuar preso em
        // AGUARDANDO_SERVICO/AGUARDANDO_DIA etc.
        conversationStateService.limparEstado(completion.fromPhone());

        String response = mensagemConfirmacao(outcome.get());
        receiptStore.completeProcessing(
                completion.provider(), completion.externalMessageId(), response);
        return ProcessOutcome.processed(completion.fromPhone(), response);
    }

    private static boolean isReceiptUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException) {
                boolean uniqueViolation =
                        SQLSTATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState());
                String message = sqlException.getMessage() == null ? "" : sqlException.getMessage();
                if (uniqueViolation && message.contains(RECEIPT_UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FlowConfirmationOutcome outcomeFromBooking(BookingIdempotencyRecord record) {
        return new FlowConfirmationOutcome(record.servico(), record.dataIso(), record.horario());
    }

    private static String mensagemConfirmacao(FlowConfirmationOutcome outcome) {
        String data = outcome.dataIso();
        try {
            data = LocalDate.parse(outcome.dataIso()).format(DATA_BR);
        } catch (RuntimeException ignored) {
            // Recibo historico pode ter representacao diferente; preserva o valor gravado.
        }

        return "Recebi seu agendamento ✅\n\n"
                + outcome.servicoNome() + " para " + data + " as "
                + formatarHorario(outcome.horario()) + ".\n\n"
                + "Posso te ajudar com algo mais?\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar";
    }

    private static String formatarHorario(String horario) {
        if (horario == null) {
            return "";
        }
        if (horario.matches("^\\d{2}:00$")) {
            return Integer.parseInt(horario.substring(0, 2)) + "h";
        }
        return horario;
    }
}
