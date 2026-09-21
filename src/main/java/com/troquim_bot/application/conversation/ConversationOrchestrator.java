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
import com.troquim_bot.application.messaging.FlowCompletionProcessor;
import com.troquim_bot.application.messaging.InboundFlowCompletion;
import com.troquim_bot.application.messaging.ProcessOutcome;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final FlowCompletionProcessor flowCompletionProcessor;
    private final Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ReentrantLock> locksPorNumero = new ConcurrentHashMap<>();

    @Autowired
    public ConversationOrchestrator(ConversationMessageProcessor conversationMessageProcessor,
                                    WhatsAppAdapter whatsAppAdapter,
                                    IntentEngine intentEngine,
                                    StrictMvpMenuService strictMvpMenuService,
                                    ConversationStateService conversationStateService,
                                    ObjectProvider<FlowCompletionProcessor> flowCompletionProcessor) {
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
        this.flowCompletionProcessor = flowCompletionProcessor.getIfAvailable();
    }

    /** Compatibilidade para testes/unitarios sem Flow habilitado. */
    public ConversationOrchestrator(ConversationMessageProcessor conversationMessageProcessor,
                                    WhatsAppAdapter whatsAppAdapter,
                                    IntentEngine intentEngine,
                                    StrictMvpMenuService strictMvpMenuService,
                                    ConversationStateService conversationStateService) {
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
        this.flowCompletionProcessor = null;
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

    private void processarConclusaoFlow(InboundFlowCompletion completion) {
        if (flowCompletionProcessor == null) {
            logger.warn("Conclusao de Flow recebida, mas capacidade de Flow nao esta disponivel.");
            return;
        }

        String numero = completion.fromPhone();
        ReentrantLock lock = locksPorNumero.computeIfAbsent(numero, k -> new ReentrantLock());
        lock.lock();
        try {
            ProcessOutcome outcome = flowCompletionProcessor.processOnce(completion);
            if (!outcome.processed() || outcome.responseText() == null
                    || outcome.responseText().isBlank()) {
                return;
            }

            enviarResposta(numero, outcome.responseText());
            flowCompletionProcessor.markSent(completion);
            logger.info("Confirmacao pos-Flow enviada (provider={}, id={}).",
                    completion.provider(), completion.externalMessageId());
        } catch (RuntimeException falha) {
            // Receipt permanece PENDING; uma reentrega pode tentar apenas o outbound.
            logger.error("Falha ao concluir Flow (provider={}, id={}, erro={}).",
                    completion.provider(), completion.externalMessageId(),
                    falha.getClass().getSimpleName());
            throw falha;
        } finally {
            lock.unlock();
        }
    }

    private void enviarResposta(String numero, String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return;
        }

        List<WhatsAppAdapter.QuickReply> opcoes = opcoesRapidas(resposta);
        if (!opcoes.isEmpty()) {
            whatsAppAdapter.enviarOpcoes(numero, resposta, opcoes);
            return;
        }

        List<WhatsAppAdapter.ListItem> lista = listaClicavel(resposta);
        if (!lista.isEmpty()) {
            whatsAppAdapter.enviarLista(numero, resposta, lista);
            return;
        }

        whatsAppAdapter.enviarMensagem(numero, resposta);
    }

    /**
     * Somente apresenta como botao decisoes que a Conversation ja tornou explicitas.
     * Nenhuma regra de negocio nasce aqui; se o provider nao suporta, o texto continua
     * exatamente o mesmo.
     */
    private List<WhatsAppAdapter.ListItem> listaClicavel(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return List.of();
        }

        java.util.ArrayList<WhatsAppAdapter.ListItem> itens = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)\\)\\s+(.+)$");

        for (String linha : resposta.split("\\R")) {
            java.util.regex.Matcher matcher = pattern.matcher(linha.trim());
            if (!matcher.matches()) {
                continue;
            }
            String id = matcher.group(1);
            String titulo = matcher.group(2).trim();
            if (!titulo.isBlank()) {
                itens.add(new WhatsAppAdapter.ListItem(id, titulo, ""));
            }
        }

        // Listas do WhatsApp sao adequadas para escolhas curtas; listas de horarios
        // muito grandes permanecem no texto ate introduzirmos paginacao explicita.
        if (itens.size() < 2 || itens.size() > 10) {
            return List.of();
        }
        return List.copyOf(itens);
    }

    private List<WhatsAppAdapter.QuickReply> opcoesRapidas(String resposta) {
        if (resposta.contains("1) Agendar")
                && resposta.contains("2) Meus agendamentos")
                && resposta.contains("3) Cancelar")) {
            return List.of(
                    new WhatsAppAdapter.QuickReply("menu_agendar", "Agendar"),
                    new WhatsAppAdapter.QuickReply("menu_meus_agendamentos", "Meus agendamentos"),
                    new WhatsAppAdapter.QuickReply("menu_cancelar", "Cancelar"));
        }

        if ((resposta.contains("1) Sim") && resposta.contains("2) Nao"))
                || (resposta.contains("1) Confirmar") && resposta.contains("2) Cancelar"))) {
            return List.of(
                    new WhatsAppAdapter.QuickReply("confirmar_sim", "Confirmar"),
                    new WhatsAppAdapter.QuickReply("confirmar_nao", "Cancelar"));
        }

        return List.of();
    }

    public void receberWebhookWhatsApp(String payload) throws Exception {
        logger.info("=== Webhook WhatsApp recebido ===");
        logger.info("Timestamp: {}", LocalDateTime.now());

        Optional<InboundFlowCompletion> flowCompletion = whatsAppAdapter.receberConclusaoFlow(payload);
        if (flowCompletion.isPresent()) {
            processarConclusaoFlow(flowCompletion.get());
            return;
        }

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
                    enviarResposta(numero, resposta);

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
