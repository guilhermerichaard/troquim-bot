package com.troquim_bot.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.evolution.EvolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet();
    private final ConversationApplicationService conversationApplicationService;
    private final EvolutionService evolutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ReentrantLock> locksPorNumero = new ConcurrentHashMap<>();

    public WebhookController(EvolutionService evolutionService, ConversationApplicationService conversationApplicationService) {
        this.evolutionService = evolutionService;
        this.conversationApplicationService = conversationApplicationService;
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
        System.out.println("messageId: " + messageId);
        System.out.println("mensagem: " + mensagem);

        // Obter lock específico para este número (serializa processamento por contato)
        ReentrantLock lock = locksPorNumero.computeIfAbsent(numero, k -> new ReentrantLock());
        
        try {
            // Tenta adquirir lock com timeout curto para não bloquear indefinidamente
            if (lock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) {
                try {
                    System.out.println("Processando mensagem - messageId: " + messageId + ", numero: " + numero);
                    
                    String resposta = conversationApplicationService.processarMensagem(numero, mensagem);
                    evolutionService.enviarMensagem(numero, resposta);

                    System.out.println("Resposta enviada - messageId: " + messageId + ", numero: " + numero + ", resposta: " + resposta);

                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("Mensagem ignorada (processamento anterior em andamento) - messageId: " + messageId + ", numero: " + numero);
            }

        } catch (Exception e) {
            System.out.println("Erro ao processar mensagem - messageId: " + messageId + ", numero: " + numero);
            e.printStackTrace();
        }

        return ResponseEntity.ok("ok");
    }
}