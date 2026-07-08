package com.troquim_bot.application.conversation;

import com.troquim_bot.application.conversation.engine.ContextStep;
import com.troquim_bot.application.conversation.engine.ConversationPipeline;
import com.troquim_bot.application.conversation.engine.DefaultEntityExtractor;
import com.troquim_bot.application.conversation.engine.EntityExtractionStep;
import com.troquim_bot.application.conversation.engine.FlowDispatcherStep;
import com.troquim_bot.application.conversation.engine.GreetingResponseStep;
import com.troquim_bot.application.conversation.engine.IntentDetectionStep;
import com.troquim_bot.application.conversation.engine.LegacyConversationProcessorStep;
import com.troquim_bot.application.conversation.engine.ResponseBuilder;
import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ConversationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ConversationOrchestrator.class);

    private final ConversationPipeline conversationPipeline;
    private final WhatsAppAdapter whatsAppAdapter;
    private final StrictMvpMenuService strictMvpMenuService;
    private final ConversationStateService conversationStateService;
    private final Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ReentrantLock> locksPorNumero = new ConcurrentHashMap<>();

    public ConversationOrchestrator(ConversationMessageProcessor conversationMessageProcessor,
                                    WhatsAppAdapter whatsAppAdapter,
                                    IntentEngine intentEngine,
                                    StrictMvpMenuService strictMvpMenuService,
                                    ConversationStateService conversationStateService) {
        if (conversationMessageProcessor == null) {
            throw new IllegalArgumentException("ConversationMessageProcessor e obrigatorio");
        }
        if (whatsAppAdapter == null) {
            throw new IllegalArgumentException("WhatsAppAdapter e obrigatorio");
        }
        if (intentEngine == null) {
            throw new IllegalArgumentException("IntentEngine e obrigatorio");
        }
        if (strictMvpMenuService == null) {
            throw new IllegalArgumentException("StrictMvpMenuService e obrigatorio");
        }
        if (conversationStateService == null) {
            throw new IllegalArgumentException("ConversationStateService e obrigatorio");
        }
        this.conversationPipeline = new ConversationPipeline(List.of(
            new IntentDetectionStep(intentEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new ContextStep(),
            new FlowDispatcherStep(),
            new GreetingResponseStep(new ResponseBuilder()),
            new LegacyConversationProcessorStep(conversationMessageProcessor)
        ));
        this.whatsAppAdapter = whatsAppAdapter;
        this.strictMvpMenuService = strictMvpMenuService;
        this.conversationStateService = conversationStateService;
    }

    public String processarMensagem(String numero, String mensagem) {
        // STRICT_MVP: intercepta antes da pipeline para garantir que o menu guiado
        // tenha prioridade sobre GREETING e outros flows que finalizam o contexto
        if (strictMvpMenuService.isStrictMvpEnabled()) {
            var state = conversationStateService.buscarPorNumero(numero);
            String respostaMenu = strictMvpMenuService.processarMenu(numero, mensagem, state);
            if (respostaMenu != null) {
                return respostaMenu;
            }
        }

        return conversationPipeline.processar(numero, mensagem);
    }

    public void receberWebhookWhatsApp(String payload) throws Exception {
        logger.info("=== Webhook WhatsApp recebido ===");
        logger.info("Timestamp: {}", LocalDateTime.now());

        Optional<WhatsAppAdapter.IncomingMessage> incomingMessage = whatsAppAdapter.receberMensagem(payload);
        if (incomingMessage.isEmpty()) {
            logger.info("Webhook WhatsApp ignorado.");
            return;
        }

        WhatsAppAdapter.IncomingMessage message = incomingMessage.get();

        if (!mensagensProcessadas.add(message.messageId())) {
            logger.info("Mensagem duplicada ignorada: {}", message.messageId());
            return;
        }

        String numero = message.numero();

        logger.info("remoteJid: {}", numero);
        logger.info("sender: {}", message.sender());
        logger.info("numero usado: {}", numero);
        logger.info("messageId: {}", message.messageId());
        logger.info("mensagem: {}", message.mensagem());

        ReentrantLock lock = locksPorNumero.computeIfAbsent(numero, k -> new ReentrantLock());

        try {
            if (lock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) {
                try {
                    logger.info("Processando mensagem - messageId: {}, numero: {}", message.messageId(), numero);

                    String resposta = processarMensagem(numero, message.mensagem());
                    whatsAppAdapter.enviarMensagem(numero, resposta);

                    logger.info("Resposta enviada - messageId: {}, numero: {}, resposta: {}",
                        message.messageId(), numero, resposta);
                } finally {
                    lock.unlock();
                }
            } else {
                logger.info("Mensagem ignorada (processamento anterior em andamento) - messageId: {}, numero: {}",
                    message.messageId(), numero);
            }
        } catch (Exception e) {
            logger.error("Erro ao processar mensagem - messageId: {}, numero: {}", message.messageId(), numero, e);
        }
    }
}
