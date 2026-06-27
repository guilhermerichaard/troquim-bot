package com.troquim_bot.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.evolution.EvolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final EvolutionService evolutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<String> receberWebhook(@RequestBody String payload) throws Exception {

        System.out.println("=== Webhook WhatsApp recebido ===");
        System.out.println("Timestamp: " + LocalDateTime.now());
        System.out.println("Payload:");
        System.out.println(payload);
        System.out.println("================================");

        JsonNode root = objectMapper.readTree(payload);
        System.out.println(root.toPrettyString());

        // Ignora mensagens enviadas por você
        boolean fromMe = root.path("data").path("key").path("fromMe").asBoolean();

        if (fromMe) {
            System.out.println("Mensagem minha. Ignorando...");
            return ResponseEntity.ok("ok");
        }

        String remoteJid = root.path("data").path("key").path("remoteJid").asText();
        String sender = root.path("sender").asText();
        String mensagem = root.path("data").path("message").path("conversation").asText();

        // Mapeamento temporário do LID para telefone
        Map<String, String> lidParaNumero = Map.of(
                "139440878043302@lid", "5511922047123"
        );

        String numero = lidParaNumero.getOrDefault(remoteJid, "5511922047123");

        System.out.println("remoteJid: " + remoteJid);
        System.out.println("sender: " + sender);
        System.out.println("numero usado: " + numero);
        System.out.println("mensagem: " + mensagem);

        try {
            evolutionService.enviarMensagem(
                    numero,
                    "Olá! Sou o assistente do Gostosão. Recebi: " + mensagem + " 🤖"
            );

            System.out.println("Mensagem enviada com sucesso!");

        } catch (Exception e) {

            System.out.println("Erro ao enviar mensagem:");
            e.printStackTrace();
        }

        return ResponseEntity.ok("ok");
    }
}