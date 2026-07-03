package com.troquim_bot.webhook;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final ConversationApplicationService conversationApplicationService;

    public WebhookController(ConversationApplicationService conversationApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<String> receberWebhook(@RequestBody String payload) throws Exception {
        conversationApplicationService.receberWebhookWhatsApp(payload);
        return ResponseEntity.ok("ok");
    }
}
