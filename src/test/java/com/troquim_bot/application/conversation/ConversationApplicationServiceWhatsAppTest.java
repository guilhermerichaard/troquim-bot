package com.troquim_bot.application.conversation;

import com.troquim_bot.repository.InMemoryConversationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationApplicationServiceWhatsAppTest {

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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);
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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);
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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);
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
        ConversationOrchestrator orchestrator = new ConversationOrchestrator(messageProcessor, whatsAppAdapter);
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
