package com.troquim_bot.application.messaging;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.infrastructure.whatsappcloud.ConditionalOnWhatsAppCloud;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limite transacional da idempotência durável (padrão "inbox").
 *
 * {@link #processOnce} roda numa ÚNICA transação: reivindica o receipt (claim) ANTES
 * de avançar a conversa e, se a conversa persistir estado, tudo comita junto:
 *
 * <ul>
 *   <li>já comitado como SENT → retorna "sem ação" (200), sem chamar a conversa nem outbound;</li>
 *   <li>já comitado como PENDING (negócio feito, mas o outbound falhou antes) → NÃO chama a
 *       conversa de novo; retorna a resposta PERSISTIDA para o orquestrador apenas REENVIAR;</li>
 *   <li>entrega concorrente com o mesmo id → o {@code claimPending} (INSERT + flush) bloqueia na
 *       UNIQUE constraint; a perdedora recebe DataIntegrityViolationException, que PROPAGA
 *       para o orquestrador tratar como duplicata (sem duplicar ação de negócio);</li>
 *   <li>falha ANTES do commit (na conversa ou no claim) → rollback total → permite retry;</li>
 *   <li>sucesso → receipt (PENDING + resposta persistida) + estado da conversa comitam
 *       atomicamente → re-entrega futura não reprocessa (não duplica Appointment/Reservation).</li>
 * </ul>
 *
 * O envio outbound acontece FORA desta transação (no orquestrador), pois é I/O externo
 * não transacional; sua falha não pode desfazer o receipt (senão o retry duplicaria a ação).
 * A resposta fica persistida (PENDING) até o outbound confirmar (SENT) — a resposta não se
 * perde definitivamente: uma re-entrega tenta somente o outbound.
 *
 * Reutiliza o fluxo real de conversa via {@link ConversationApplicationService#processarMensagem}
 * — sem reimplementar Conversation nem regras de agendamento.
 */
@Service
@ConditionalOnWhatsAppCloud
public class InboundReceiptProcessor {

    // Constraint da tabela de receipts (V3). SQLState 23505 = unique_violation (Postgres).
    private static final String RECEIPT_UNIQUE_CONSTRAINT = "uq_inbound_receipt_provider_external_id";
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private final InboundReceiptStore receiptStore;
    private final ConversationApplicationService conversationApplicationService;

    public InboundReceiptProcessor(InboundReceiptStore receiptStore,
                                   ConversationApplicationService conversationApplicationService) {
        if (receiptStore == null) {
            throw new IllegalArgumentException("InboundReceiptStore e obrigatorio");
        }
        if (conversationApplicationService == null) {
            throw new IllegalArgumentException("ConversationApplicationService e obrigatorio");
        }
        this.receiptStore = receiptStore;
        this.conversationApplicationService = conversationApplicationService;
    }

    /**
     * Processa a mensagem exatamente uma vez. Pode lançar
     * {@code org.springframework.dao.DataIntegrityViolationException} numa corrida de
     * entregas concorrentes — o orquestrador trata como duplicata.
     *
     * Retorno: {@code processed=true} indica "há resposta a (re)enviar" (novo processamento
     * OU re-entrega de um PENDING cujo outbound falhou); {@code processed=false} indica nada
     * a fazer (já SENT ou corrida concorrente).
     */
    @Transactional
    public ProcessOutcome processOnce(InboundTextMessage message) {
        var existing = receiptStore.find(message.provider(), message.externalMessageId());
        if (existing.isPresent()) {
            if (InboundReceiptStore.STATUS_SENT.equals(existing.get().status())) {
                return ProcessOutcome.duplicate();   // já entregue → 200, nada a fazer
            }
            // PENDING: negócio já processado antes; NÃO chama a conversa de novo —
            // apenas reenvia a resposta persistida.
            return ProcessOutcome.processed(message.fromPhone(), existing.get().responseText());
        }

        // Claim ANTES de avançar a conversa: serializa concorrência via UNIQUE constraint.
        // SOMENTE a violação da UNIQUE(provider, external_message_id) do receipt vira duplicata
        // (entrega concorrente com o mesmo id). Qualquer OUTRA DataIntegrityViolationException —
        // inclusive vinda do claim, ex.: outra constraint — PROPAGA para reentrega/reprocessamento.
        try {
            receiptStore.claimPending(message.provider(), message.externalMessageId());
        } catch (DataIntegrityViolationException integrityViolation) {
            if (isReceiptUniqueViolation(integrityViolation)) {
                throw new ConcurrentReceiptClaimException(
                        message.provider(), message.externalMessageId(), integrityViolation);
            }
            throw integrityViolation;
        }

        String response = conversationApplicationService.processarMensagem(
                message.fromPhone(), message.text());

        // Persiste a resposta (ainda PENDING) atomicamente com o avanço da conversa.
        receiptStore.completeProcessing(message.provider(), message.externalMessageId(), response);

        return ProcessOutcome.processed(message.fromPhone(), response);
    }

    /** Marca SENT após envio outbound bem-sucedido. Transação própria, curta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(InboundTextMessage message, OutboundResult result) {
        receiptStore.markSent(message.provider(), message.externalMessageId(),
                result == null ? null : result.externalMessageId());
    }

    /**
     * Verdadeiro somente se a violação for a UNIQUE(provider, external_message_id) do receipt.
     * Identifica pela SQLState 23505 (unique_violation) E pelo nome da constraint na cadeia de
     * causas — não trata genericamente qualquer erro de integridade como duplicata.
     */
    private static boolean isReceiptUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException) {
                boolean uniqueViolation = SQLSTATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState());
                String message = sqlException.getMessage() == null ? "" : sqlException.getMessage();
                if (uniqueViolation && message.contains(RECEIPT_UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }
        }
        return false;
    }
}
