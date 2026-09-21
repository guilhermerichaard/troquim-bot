package com.troquim_bot.application.conversation;

import com.troquim_bot.application.messaging.OutboundInteractiveOption;
import com.troquim_bot.evolution.EvolutionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
    void deveExtrairConclusaoDeFlowDoInteractiveResponseDaEvolution() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        var completion = adapter.receberConclusaoFlow("""
            {
              "event": "messages.upsert",
              "sender": "5511999999999",
              "data": {
                "key": {
                  "id": "flow-message-1",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": false
                },
                "messageTimestamp": 1700000000,
                "message": {
                  "interactiveResponseMessage": {
                    "nativeFlowResponseMessage": {
                      "name": "flow",
                      "paramsJson": "{\\\"flow_token\\\":\\\"token-abc\\\",\\\"servico\\\":\\\"nao-confiar\\\"}"
                    }
                  }
                }
              }
            }
            """);

        assertTrue(completion.isPresent());
        assertEquals("evolution", completion.get().provider());
        assertEquals("flow-message-1", completion.get().externalMessageId());
        assertEquals("5511999999999", completion.get().fromPhone());
        assertEquals("token-abc", completion.get().flowToken());
    }

    @Test
    void conclusaoDeFlowNaoViraMensagemTextual() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        String payload = """
            {
              "event": "messages.upsert",
              "sender": "5511999999999",
              "data": {
                "key": {
                  "id": "flow-message-2",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": false
                },
                "message": {
                  "nativeFlowResponseMessage": {
                    "paramsJson": "{\\\"flow_token\\\":\\\"token-def\\\"}"
                  }
                }
              }
            }
            """;

        assertTrue(adapter.receberConclusaoFlow(payload).isPresent());
        assertTrue(adapter.receberMensagem(payload).isEmpty());
    }

    @Test
    void deveConverterCliqueEmBotaoParaComandoCanonico() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        Optional<WhatsAppAdapter.IncomingMessage> message = adapter.receberMensagem("""
            {
              "event": "messages.upsert",
              "sender": "5511999999999",
              "data": {
                "key": {
                  "id": "button-message-1",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": false
                },
                "message": {
                  "buttonsResponseMessage": {
                    "selectedButtonId": "menu_agendar"
                  }
                }
              }
            }
            """);

        assertTrue(message.isPresent());
        assertEquals("1", message.get().mensagem());
    }

    @Test
    void deveDelegarQuickRepliesParaSendButtons() {
        RecordingEvolutionService evolutionService = new RecordingEvolutionService();
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(evolutionService);

        adapter.enviarOpcoes(
                "5511999999999",
                "Posso ajudar?",
                List.of(
                        new OutboundInteractiveOption("menu_agendar", "Agendar"),
                        new OutboundInteractiveOption("menu_cancelar", "Cancelar")));

        assertEquals("5511999999999", evolutionService.numeroBotoes);
        assertEquals("Posso ajudar?", evolutionService.descricaoBotoes);
        assertEquals(2, evolutionService.botoes.size());
        assertEquals("reply", evolutionService.botoes.get(0).get("type"));
        assertEquals("menu_agendar", evolutionService.botoes.get(0).get("id"));
    }

    @Test
    void deveDelegarListaClicavelParaSendList() {
        RecordingEvolutionService evolutionService = new RecordingEvolutionService();
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(evolutionService);

        adapter.enviarLista(
                "5511999999999",
                "Escolha o servico",
                List.of(
                        new OutboundInteractiveOption("1", "Manicure", ""),
                        new OutboundInteractiveOption("2", "Design de sobrancelhas", "")));

        assertEquals("5511999999999", evolutionService.numeroLista);
        assertEquals("Escolha o servico", evolutionService.descricaoLista);
        assertEquals(1, evolutionService.secoes.size());
    }

    @Test
    void deveConverterSelecaoDeListaParaNumeroCanonico() throws Exception {
        EvolutionWhatsAppAdapter adapter = new EvolutionWhatsAppAdapter(new RecordingEvolutionService());

        Optional<WhatsAppAdapter.IncomingMessage> message = adapter.receberMensagem("""
            {
              "event": "messages.upsert",
              "sender": "5511999999999",
              "data": {
                "key": {
                  "id": "list-message-1",
                  "remoteJid": "5511999999999@s.whatsapp.net",
                  "fromMe": false
                },
                "message": {
                  "listResponseMessage": {
                    "singleSelectReply": {
                      "selectedRowId": "2"
                    }
                  }
                }
              }
            }
            """);

        assertTrue(message.isPresent());
        assertEquals("2", message.get().mensagem());
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
        private String numeroBotoes;
        private String descricaoBotoes;
        private List<Map<String, Object>> botoes = List.of();
        private String numeroLista;
        private String descricaoLista;
        private List<Map<String, Object>> secoes = List.of();

        @Override
        public void enviarMensagem(String numero, String texto) {
            this.numero = numero;
            this.texto = texto;
        }

        @Override
        public void enviarBotoes(String numero, String titulo, String descricao,
                                 List<Map<String, Object>> botoes) {
            this.numeroBotoes = numero;
            this.descricaoBotoes = descricao;
            this.botoes = botoes;
        }

        @Override
        public void enviarLista(String numero, String titulo, String descricao,
                                String textoBotao, List<Map<String, Object>> secoes) {
            this.numeroLista = numero;
            this.descricaoLista = descricao;
            this.secoes = secoes;
        }
    }
}
