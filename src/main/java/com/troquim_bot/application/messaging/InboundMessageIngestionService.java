package com.troquim_bot.application.messaging;

import com.troquim_bot.infrastructure.whatsappcloud.ConditionalOnWhatsAppCloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Orquestra o recebimento de eventos de webhook (camada Application), provider-neutral.
 *
 * Ordem: (1) valida a assinatura sobre os bytes brutos; (2) só então desserializa;
 * (3) para cada mensagem de texto, processa idempotentemente e (4) solicita o envio
 * da resposta pela porta outbound. NÃO decide serviço, disponibilidade, Customer,
 * Reservation, Appointment ou regra de negócio — isso é do fluxo de Conversation/Domain.
 *
 * Logs sanitizados: apenas provider e id externo (opaco). Telefone, texto, assinatura
 * e segredos NUNCA são registrados.
 */
@Service
@ConditionalOnWhatsAppCloud
public class InboundMessageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageIngestionService.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final InboundMessageParser parser;
    private final InboundReceiptProcessor receiptProcessor;
    private final OutboundMessageGateway outboundGateway;
    private final FlowCompletionProcessor flowCompletionProcessor;

    // Serializa o processamento por telefone (mesma conversa) para evitar o read-modify-write
    // concorrente de ConversationState (lost update). Mesmo padrão in-memory já usado no fluxo
    // Evolution (ConversationOrchestrator.locksPorNumero). O lock envolve também o envio/markSent:
    // do contrário uma entrega concorrente veria o receipt ainda PENDING e reenviaria (double-send).
    // Não segura conexão de banco durante o outbound — a transação do processOnce já comitou.
    private final ConcurrentHashMap<String, ReentrantLock> locksPorNumero = new ConcurrentHashMap<>();

    @Autowired
    public InboundMessageIngestionService(WebhookSignatureVerifier signatureVerifier,
                                          InboundMessageParser parser,
                                          InboundReceiptProcessor receiptProcessor,
                                          OutboundMessageGateway outboundGateway,
                                          ObjectProvider<FlowCompletionProcessor> flowCompletionProcessor) {
        this.signatureVerifier = signatureVerifier;
        this.parser = parser;
        this.receiptProcessor = receiptProcessor;
        this.outboundGateway = outboundGateway;
        this.flowCompletionProcessor = flowCompletionProcessor.getIfAvailable();
    }

    /** Compatibilidade para testes/unitarios sem capacidade de Flow. */
    public InboundMessageIngestionService(WebhookSignatureVerifier signatureVerifier,
                                          InboundMessageParser parser,
                                          InboundReceiptProcessor receiptProcessor,
                                          OutboundMessageGateway outboundGateway) {
        this.signatureVerifier = signatureVerifier;
        this.parser = parser;
        this.receiptProcessor = receiptProcessor;
        this.outboundGateway = outboundGateway;
        this.flowCompletionProcessor = null;
    }

    public IngestOutcome ingest(byte[] rawBody, String signatureHeader) {
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            log.warn("Webhook rejeitado: assinatura ausente ou invalida.");
            return IngestOutcome.SIGNATURE_INVALID;
        }

        ParsedInboundPayload parsed;
        try {
            parsed = parser.parse(rawBody);
        } catch (InboundMessageParser.InboundPayloadFormatException e) {
            log.warn("Webhook com payload malformado (ignorado).");
            return IngestOutcome.MALFORMED;
        }

        boolean outboundFailed = false;
        for (InboundTextMessage message : parsed.textMessages()) {
            if (!processOne(message)) {
                outboundFailed = true;
            }
        }
        for (InboundFlowCompletion completion : parsed.flowCompletions()) {
            if (!processFlowCompletion(completion)) {
                outboundFailed = true;
            }
        }
        // Se algum envio outbound falhou, NÃO retornar 2xx: a WhatsApp Cloud API só reentrega
        // o evento em resposta não-2xx. O 503 faz a Meta reentregar, e a re-entrega tenta
        // somente o outbound (a conversa não é reprocessada — receipt PENDING).
        return outboundFailed ? IngestOutcome.OUTBOUND_UNAVAILABLE : IngestOutcome.ACCEPTED;
    }

    private boolean processFlowCompletion(InboundFlowCompletion completion) {
        FlowCompletionProcessor processor = flowCompletionProcessor;
        if (processor == null) {
            // Flow desligado: reconhece o evento sem efeito. Nao e erro de transporte.
            return true;
        }

        ReentrantLock lock = locksPorNumero.computeIfAbsent(
                completion.fromPhone(), k -> new ReentrantLock());
        lock.lock();
        try {
            ProcessOutcome outcome;
            try {
                outcome = processor.processOnce(completion);
            } catch (ConcurrentReceiptClaimException concurrentDuplicate) {
                log.info("Conclusao de Flow duplicada concorrente ignorada (provider={}, id={}).",
                        completion.provider(), completion.externalMessageId());
                return true;
            }

            if (!outcome.processed()) {
                return true;
            }
            return sendReply(
                    completion.provider(),
                    completion.externalMessageId(),
                    completion.fromPhone(),
                    outcome.responseText());
        } finally {
            lock.unlock();
        }
    }

    /** @return {@code false} se o envio outbound falhou (evento deve ser reentregue pela Meta). */
    private boolean processOne(InboundTextMessage message) {
        // Serializa por telefone TODO o processamento da mensagem (avanço da conversa + envio +
        // markSent): evita o lost update de ConversationState e o double-send de uma entrega
        // concorrente que veria o receipt ainda PENDING.
        ReentrantLock lock = locksPorNumero.computeIfAbsent(message.fromPhone(), k -> new ReentrantLock());
        lock.lock();
        try {
            ProcessOutcome outcome;
            try {
                outcome = receiptProcessor.processOnce(message);
            } catch (ConcurrentReceiptClaimException concurrentDuplicate) {
                // Conflito do CLAIM (entrega concorrente com o mesmo id): duplicata → 200.
                log.info("Entrega concorrente duplicada ignorada (provider={}, id={}).",
                        message.provider(), message.externalMessageId());
                return true;
            }

            if (!outcome.processed()) {
                log.info("Entrega duplicada ignorada (provider={}, id={}).",
                        message.provider(), message.externalMessageId());
                return true;
            }

            return sendReply(
                    message.provider(), message.externalMessageId(),
                    message.fromPhone(), outcome.responseText());
        } finally {
            lock.unlock();
        }
    }

    /** @return {@code false} se o envio outbound falhou. */
    private boolean sendReply(String provider, String externalMessageId,
                              String fromPhone, String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return true;
        }
        try {
            var presentation = ConversationInteractivePresentation.from(responseText);
            OutboundResult result;
            if (presentation.isEmpty()) {
                result = outboundGateway.sendText(fromPhone, responseText);
            } else if (presentation.get().type() == ConversationInteractivePresentation.Type.BUTTONS) {
                result = outboundGateway.sendButtons(
                        fromPhone, responseText, presentation.get().options());
            } else {
                result = outboundGateway.sendList(
                        fromPhone, responseText, presentation.get().options());
            }
            receiptProcessor.markSent(provider, externalMessageId, result);
            return true;
        } catch (RuntimeException outboundFailure) {
            // O receipt ja esta duravel (PENDING) com a resposta persistida. Nao
            // reprocessa o evento; a reentrega tenta somente o outbound.
            log.error("Falha no envio outbound (provider={}, id={}, erro={}). Receipt PENDING; solicitando reentrega.",
                    provider, externalMessageId, outboundFailure.getClass().getSimpleName());
            return false;
        }
    }
}
