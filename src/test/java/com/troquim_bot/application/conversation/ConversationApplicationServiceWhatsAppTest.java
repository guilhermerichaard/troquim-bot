package com.troquim_bot.application.conversation;

import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.repository.InMemoryConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationApplicationServiceWhatsAppTest {

    private StrictMvpMenuService strictMvpMenuService;
    private ConversationStateService conversationStateService;

    @BeforeEach
    void setUp() {
        strictMvpMenuService = mock(StrictMvpMenuService.class);
        when(strictMvpMenuService.isStrictMvpEnabled()).thenReturn(false);
        conversationStateService = mock(ConversationStateService.class);
    }

    @Test
    void deveReceberWebhookProcessarMensagemEEnviarRespostaPeloAdapter() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-1",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        TestConversationMessageProcessor messageProcessor = new TestConversationMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);
        ConversationApplicationService applicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            orchestrator,
            new ConversationInputMapper()
        );

        applicationService.receberWebhookWhatsApp("payload");

        assertEquals("payload", whatsAppAdapter.payloadRecebido);
        assertEquals("5511999999999@s.whatsapp.net", messageProcessor.numeroRecebido);
        assertEquals("Oi", messageProcessor.mensagemRecebida);
        assertEquals("5511999999999@s.whatsapp.net", whatsAppAdapter.numeroEnviado);
        assertEquals("Ola", whatsAppAdapter.textoEnviado);
    }

    @Test
    void deveIgnorarWebhookSemMensagemRecebida() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(Optional.empty());
        TestConversationMessageProcessor messageProcessor = new TestConversationMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);
        ConversationApplicationService applicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            orchestrator,
            new ConversationInputMapper()
        );

        applicationService.receberWebhookWhatsApp("payload");

        assertNull(messageProcessor.numeroRecebido);
        assertNull(whatsAppAdapter.numeroEnviado);
    }

    @Test
    void deveIgnorarMensagemDuplicada() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-1",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        TestConversationMessageProcessor messageProcessor = new TestConversationMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);
        ConversationApplicationService applicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            orchestrator,
            new ConversationInputMapper()
        );

        applicationService.receberWebhookWhatsApp("payload");
        applicationService.receberWebhookWhatsApp("payload");

        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals(1, whatsAppAdapter.quantidadeEnviada);
    }

    @Test
    void deveDelegarProcessamentoDiretoParaOrchestrator() {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(Optional.empty());
        TestConversationMessageProcessor messageProcessor = new TestConversationMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);
        ConversationApplicationService applicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            orchestrator,
            new ConversationInputMapper()
        );

        String resposta = applicationService.processarMensagem("5511999999999", "Oi");

        assertEquals("Ola", resposta);
        assertEquals("5511999999999", messageProcessor.numeroRecebido);
        assertEquals("Oi", messageProcessor.mensagemRecebida);
    }

    @Test
    void deveProcessarApenasUmaVezCom10ChamadasSimultaneasMesmoMessageId() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-concurrent",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        TestConversationMessageProcessor messageProcessor = new TestConversationMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);
        ConversationApplicationService applicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            orchestrator,
            new ConversationInputMapper()
        );

        int numeroThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numeroThreads);
        CountDownLatch latch = new CountDownLatch(numeroThreads);

        for (int i = 0; i < numeroThreads; i++) {
            executor.submit(() -> {
                try {
                    applicationService.receberWebhookWhatsApp("payload");
                } catch (Exception e) {
                    // Ignore exceptions for this test
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, messageProcessor.quantidadeProcessada, "Apenas 1 processamento deve ocorrer");
        assertEquals(1, whatsAppAdapter.quantidadeEnviada, "Apenas 1 envio deve ocorrer");
    }

    private static class RecordingWhatsAppAdapter implements WhatsAppAdapter {
        private final Optional<IncomingMessage> incomingMessage;
        private String payloadRecebido;
        private String numeroEnviado;
        private String textoEnviado;
        private int quantidadeEnviada;

        private RecordingWhatsAppAdapter(Optional<IncomingMessage> incomingMessage) {
            this.incomingMessage = incomingMessage;
        }

        @Override
        public Optional<IncomingMessage> receberMensagem(String payload) {
            payloadRecebido = payload;
            return incomingMessage;
        }

        @Override
        public void enviarMensagem(String numero, String texto) {
            numeroEnviado = numero;
            textoEnviado = texto;
            quantidadeEnviada++;
        }
    }

    private static class TestConversationMessageProcessor implements ConversationMessageProcessor {
        private final String resposta;
        private String numeroRecebido;
        private String mensagemRecebida;
        private int quantidadeProcessada;

        private TestConversationMessageProcessor(String resposta) {
            this.resposta = resposta;
        }

        @Override
        public String gerarResposta(String numero, String mensagem) {
            numeroRecebido = numero;
            mensagemRecebida = mensagem;
            quantidadeProcessada++;
            return resposta;
        }
    }
}
