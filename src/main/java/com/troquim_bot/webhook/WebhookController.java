package com.troquim_bot.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.conversation.ConversationService;
import com.troquim_bot.evolution.EvolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet();
    private final ConversationService conversationService;
    private final EvolutionService evolutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(EvolutionService evolutionService, ConversationService conversationService) {
        this.evolutionService = evolutionService;
        this.conversationService = conversationService;
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<String> receberWebhook(@RequestBody String payload) throws Exception {

        System.out.println("=== Webhook WhatsApp recebido ===");
        System.out.println("Timestamp: " + LocalDateTime.now());

        JsonNode root = objectMapper.readTree(payload);

        String event = root.path("event").asText();

        if (!"messages.upsert".equals(event)) {
            System.out.println("Evento ignorado: " + event);
            return ResponseEntity.ok("ok");
        }

        boolean fromMe = root.path("data").path("key").path("fromMe").asBoolean();

        if (fromMe) {
            System.out.println("Mensagem minha. Ignorando...");
            return ResponseEntity.ok("ok");
        }

        String messageId = root.path("data").path("key").path("id").asText();

        if (!mensagensProcessadas.add(messageId)) {
            System.out.println("Mensagem duplicada ignorada: " + messageId);
            return ResponseEntity.ok("ok");
        }

        String remoteJid = root.path("data").path("key").path("remoteJid").asText();
        String sender = root.path("sender").asText();
        String mensagem = root.path("data").path("message").path("conversation").asText();

        if (mensagem == null || mensagem.isBlank()) {
            System.out.println("Mensagem vazia ou não textual. Ignorando...");
            return ResponseEntity.ok("ok");
        }

        String numero = remoteJid;

        System.out.println("remoteJid: " + remoteJid);
        System.out.println("sender: " + sender);
        System.out.println("numero usado: " + numero);
        System.out.println("mensagem: " + mensagem);

        try {
            String resposta = conversationService.gerarResposta(numero, mensagem);
            evolutionService.enviarMensagem(numero, resposta);

            System.out.println("Mensagem enviada com sucesso!");

        } catch (Exception e) {
            System.out.println("Erro ao enviar mensagem:");
            e.printStackTrace();
        }

        return ResponseEntity.ok("ok");
    }
}