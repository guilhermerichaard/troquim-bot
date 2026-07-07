package com.troquim_bot.application.conversation;

import com.troquim_bot.evolution.EvolutionService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvolutionWhatsAppAdapterTest {

    @Test
    void deveConverterPayloadDaEvolutionEmMensagemRecebida() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        Optional<WhatsAppAdapter.IncomingMessage> message = adapter.receberMensagem("""
            {
              "event": "messages.upsert",
              "sender": "5511999999999",
              "data": {
                "key": {
                  "id": "message-1",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": false
                },
                "message": {
                  "conversation": "Oi"
                }
              }
            }
            """);

        assertTrue(message.isPresent());
        assertEquals("message-1", message.get().messageId());
        assertEquals("5511999999999", message.get().numero());
        assertEquals("5511999999999", message.get().sender());
        assertEquals("Oi", message.get().mensagem());
    }

    @Test
    void deveIgnorarEventoQueNaoSejaMensagemRecebida() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        Optional<WhatsAppAdapter.IncomingMessage> message = adapter.receberMensagem("""
            {
              "event": "connection.update"
            }
            """);

        assertTrue(message.isEmpty());
    }

    @Test
    void deveIgnorarMensagemEnviadaPeloProprioNumero() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        Optional<WhatsAppAdapter.IncomingMessage> message = adapter.receberMensagem("""
            {
              "event": "messages.upsert",
              "data": {
                "key": {
                  "id": "message-1",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": true
                },
                "message": {
                  "conversation": "Oi"
                }
              }
            }
            """);

        assertTrue(message.isEmpty());
    }

    @Test
    void deveDelegarEnvioParaEvolutionService() {
        RecordingEvolutionService evolutionService = new RecordingEvolutionService();
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(evolutionService);

        adapter.enviarMensagem("5511999999999", "resposta");

        assertEquals("5511999999999", evolutionService.numero);
        assertEquals("resposta", evolutionService.texto);
    }

    private static class RecordingEvolutionService extends EvolutionService {
        private String numero;
        private String texto;

        @Override
        public void enviarMensagem(String numero, String texto) {
            this.numero = numero;
            this.texto = texto;
        }
    }
}
