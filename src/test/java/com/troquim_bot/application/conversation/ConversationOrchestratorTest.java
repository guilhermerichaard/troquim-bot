package com.troquim_bot.application.conversation;

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);

        orchestrator.receberWebhookWhatsApp("payload");
        orchestrator.receberWebhookWhatsApp("payload");

        assertEquals(1, messageProcessor.quantidadeProcessada);
        assertEquals(1, whatsAppAdapter.quantidadeEnviada);
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
