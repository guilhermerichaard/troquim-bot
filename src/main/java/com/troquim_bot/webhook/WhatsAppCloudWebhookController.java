package com.troquim_bot.webhook;

import com.troquim_bot.application.messaging.IngestOutcome;
import com.troquim_bot.application.messaging.InboundMessageIngestionService;
import com.troquim_bot.application.messaging.SubscriptionVerifier;
import com.troquim_bot.infrastructure.whatsappcloud.ConditionalOnWhatsAppCloud;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Endpoint da integração oficial WhatsApp Cloud API (Meta). Rota própria
 * ({@code /webhook/whatsapp/cloud}) para coexistir com os webhooks anteriores da
 * Evolution ({@code /webhook/whatsapp}, {@code /webhook/whatsapp/messages-upsert})
 * e permitir cutover controlado por provider.
 *
 * Camada INTERFACE apenas: lê parâmetros de verificação, captura o corpo BRUTO
 * (byte[]) e mapeia o desfecho para HTTP. A verificação de assinatura, o parsing,
 * a idempotência, a chamada da Conversation e o envio outbound são da Application/
 * Infrastructure. A rota POST é pública no Spring Security, porém protegida
 * criptograficamente pela assinatura HMAC da Meta.
 */
@RestController
@RequestMapping("/webhook/whatsapp/cloud")
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudWebhookController {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final SubscriptionVerifier subscriptionVerifier;
    private final InboundMessageIngestionService ingestionService;

    public WhatsAppCloudWebhookController(SubscriptionVerifier subscriptionVerifier,
                                          InboundMessageIngestionService ingestionService) {
        this.subscriptionVerifier = subscriptionVerifier;
        this.ingestionService = ingestionService;
    }

    /**
     * Handshake de verificação da Meta (GET). Retorna hub.challenge como texto quando
     * mode=subscribe e o verify token corresponde. O verify token nunca é registrado.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        if (mode == null || verifyToken == null || challenge == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!"subscribe".equals(mode)) {
            return ResponseEntity.badRequest().build();
        }
        if (!subscriptionVerifier.matches(verifyToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
    }

    /**
     * Recepção de eventos (POST). Captura o corpo BRUTO antes de qualquer
     * desserialização e delega à Application. Mapa de status:
     * 401 (assinatura ausente/inválida), 400 (payload malformado), 200 (aceito).
     */
    @PostMapping
    public ResponseEntity<Void> receive(HttpServletRequest request,
                                        @RequestHeader(value = SIGNATURE_HEADER, required = false)
                                        String signature) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        IngestOutcome outcome = ingestionService.ingest(rawBody, signature);
        return switch (outcome) {
            case SIGNATURE_INVALID -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case MALFORMED -> ResponseEntity.badRequest().build();
            case ACCEPTED -> ResponseEntity.ok().build();
            // Não-2xx: negócio processado/persistido, mas o outbound falhou → a Meta reentrega.
            case OUTBOUND_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        };
    }
}
