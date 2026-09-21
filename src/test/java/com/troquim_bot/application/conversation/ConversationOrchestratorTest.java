package com.troquim_bot.application.conversation;

import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import com.troquim_bot.application.messaging.OutboundInteractiveOption;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationOrchestratorTest {

    private StrictMvpMenuService strictMvpMenuService;
    private ConversationStateService conversationStateService;

    @BeforeEach
    void setUp() {
        strictMvpMenuService = mock(StrictMvpMenuService.class);
        when(strictMvpMenuService.isStrictMvpEnabled()).thenReturn(false);
        conversationStateService = mock(ConversationStateService.class);
    }

    @Test
    void deveCoordenarWebhookAteEnviarResposta() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-1",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        orchestrator.receberWebhookWhatsApp("payload");

        assertEquals("payload", whatsAppAdapter.payloadRecebido);
        assertEquals("5511999999999@s.whatsapp.net", messageProcessor.numeroRecebido);
        assertEquals("Oi", messageProcessor.mensagemRecebida);
        assertEquals("5511999999999@s.whatsapp.net", whatsAppAdapter.numeroEnviado);
        assertEquals("Ola", whatsAppAdapter.textoEnviado);
    }

    @Test
    void deveRenderizarMenuCurtoComoQuickReplies() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-menu",
                "5511999999999",
                "5511999999999",
                "Oi"
            ))
        );
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor(
                "Deseja fazer algo mais?\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar");
        IntentEngine intentEngine = message -> new IntentResult(IntentType.UNKNOWN);
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(
                messageProcessor, whatsAppAdapter, intentEngine,
                strictMvpMenuService, conversationStateService);

        orchestrator.receberWebhookWhatsApp("payload");

        assertEquals(1, whatsAppAdapter.quantidadeOpcoesEnviadas);
        assertEquals(0, whatsAppAdapter.quantidadeEnviada);
        assertEquals(3, whatsAppAdapter.opcoesEnviadas.size());
        assertEquals("menu_agendar", whatsAppAdapter.opcoesEnviadas.get(0).id());
        assertEquals("Agendar", whatsAppAdapter.opcoesEnviadas.get(0).title());
    }

    @Test
    void deveRenderizarMenuDinamicoCurtoComoListaClicavel() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-list",
                "5511999999999",
                "5511999999999",
                "1"
            ))
        );
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor(
                "Qual servico voce gostaria?\n\n"
                + "1) Manicure\n"
                + "2) Design de sobrancelhas\n"
                + "3) Escova\n\n"
                + "Digite o numero ou o nome do servico:");
        IntentEngine intentEngine = message -> new IntentResult(IntentType.UNKNOWN);
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(
                messageProcessor, whatsAppAdapter, intentEngine,
                strictMvpMenuService, conversationStateService);

        orchestrator.receberWebhookWhatsApp("payload-list");

        assertEquals(1, whatsAppAdapter.quantidadeListasEnviadas);
        assertEquals(0, whatsAppAdapter.quantidadeEnviada);
        assertEquals(3, whatsAppAdapter.listaEnviada.size());
        assertEquals("1", whatsAppAdapter.listaEnviada.get(0).id());
        assertEquals("Manicure", whatsAppAdapter.listaEnviada.get(0).title());
    }

    @Test
    void deveIgnorarMensagemDuplicadaNoOrchestrator() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-1",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        orchestrator.receberWebhookWhatsApp("payload");
        orchestrator.receberWebhookWhatsApp("payload");

        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals(1, whatsAppAdapter.quantidadeEnviada);
    }

    @Test
    void deveResponderSaudacaoParaGreeting() {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(Optional.empty());
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.GREETING);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        String resposta = orchestrator.processarMensagem("5511999999999", "Oi");

        assertEquals("Ola! Como posso ajudar voce hoje?", resposta);
        assertEquals(0, messageProcessor.quantidadeProcessada);
    }

    @Test
    void deveEncaminharBookAppointmentParaFluxoAtual() {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(Optional.empty());
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Resposta agendamento");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.BOOK_APPOINTMENT);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        String resposta = orchestrator.processarMensagem("5511999999999", "Quero agendar");

        assertEquals("Resposta agendamento", resposta);
        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals("5511999999999", messageProcessor.numeroRecebido);
        assertEquals("Quero agendar", messageProcessor.mensagemRecebida);
    }

    @Test
    void deveEncaminharUnknownParaFluxoAtual() {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(Optional.empty());
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Resposta padrao");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        String resposta = orchestrator.processarMensagem("5511999999999", "xyz");

        assertEquals("Resposta padrao", resposta);
        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals("5511999999999", messageProcessor.numeroRecebido);
        assertEquals("xyz", messageProcessor.mensagemRecebida);
    }

    @Test
    void deveProcessarApenasUmaVezCom10ChamadasSimultaneasMesmoMessageId() throws Exception {
        RecordingWhatsAppAdapter whatsAppAdapter = new RecordingWhatsAppAdapter(
            Optional.of(new WhatsAppAdapter.IncomingMessage(
                "message-duplicate",
                "5511999999999@s.whatsapp.net",
                "5511999999999",
                "Oi"
            ))
        );
        RecordingMessageProcessor messageProcessor = new RecordingMessageProcessor("Ola");
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine, strictMvpMenuService, conversationStateService);

        int numeroThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numeroThreads);
        CountDownLatch latch = new CountDownLatch(numeroThreads);

        for (int i = 0; i < numeroThreads; i++) {
            executor.submit(() -> {
                try {
                    orchestrator.receberWebhookWhatsApp("payload");
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
        private int quantidadeOpcoesEnviadas;
        private List<OutboundInteractiveOption> opcoesEnviadas = List.of();
        private int quantidadeListasEnviadas;
        private List<OutboundInteractiveOption> listaEnviada = List.of();

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

        @Override
        public void enviarOpcoes(String numero, String texto,
                                 List<OutboundInteractiveOption> opcoes) {
            numeroEnviado = numero;
            textoEnviado = texto;
            opcoesEnviadas = opcoes;
            quantidadeOpcoesEnviadas++;
        }

        @Override
        public void enviarLista(String numero, String texto,
                                List<OutboundInteractiveOption> itens) {
            numeroEnviado = numero;
            textoEnviado = texto;
            listaEnviada = itens;
            quantidadeListasEnviadas++;
        }
    }

    private static class RecordingMessageProcessor implements ConversationMessageProcessor {
        private final String resposta;
        private String numeroRecebido;
        private String mensagemRecebida;
        private int quantidadeProcessada;

        private RecordingMessageProcessor(String resposta) {
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
