package com.troquim_bot.application.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ConversationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ConversationOrchestrator.class);

    private final ConversationMessageProcessor conversationMessageProcessor;
    private final WhatsAppAdapter whatsAppAdapter;
    private final Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ReentrantLock> locksPorNumero = new ConcurrentHashMap<>();

    public ConversationOrchestrator(ConversationMessageProcessor conversationMessageProcessor,
                                    WhatsAppAdapter whatsAppAdapter) {
        if (conversationMessageProcessor == null) {
            throw new IllegalArgumentException("ConversationMessageProcessor e obrigatorio");
        }
        if (whatsAppAdapter == null) {
            throw new IllegalArgumentException("WhatsAppAdapter e obrigatorio");
        }
        this.conversationMessageProcessor = conversationMessageProcessor;
        this.whatsAppAdapter = whatsAppAdapter;
    }

    public String processarMensagem(String numero, String mensagem) {
        return conversationMessageProcessor.gerarResposta(numero, mensagem);
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
