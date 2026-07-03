package com.troquim_bot.application.conversation;

import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationOrchestratorTest {

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine);

        orchestrator.receberWebhookWhatsApp("payload");

        assertEquals("payload", whatsAppAdapter.payloadRecebido);
        assertEquals("5511999999999@s.whatsapp.net", messageProcessor.numeroRecebido);
        assertEquals("Oi", messageProcessor.mensagemRecebida);
        assertEquals("5511999999999@s.whatsapp.net", whatsAppAdapter.numeroEnviado);
        assertEquals("Ola", whatsAppAdapter.textoEnviado);
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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine);

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine);

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine);

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter, intentEngine);

        String resposta = orchestrator.processarMensagem("5511999999999", "xyz");

        assertEquals("Resposta padrao", resposta);
        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals("5511999999999", messageProcessor.numeroRecebido);
        assertEquals("xyz", messageProcessor.mensagemRecebida);
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
