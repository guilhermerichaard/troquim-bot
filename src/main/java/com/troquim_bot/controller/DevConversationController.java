package com.troquim_bot.controller;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.controller.dto.DevConversationRequest;
import com.troquim_bot.controller.dto.DevConversationResponse;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev/conversation")
public class DevConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final ConversationStateService conversationStateService;

    public DevConversationController(ConversationApplicationService conversationApplicationService,
                                     ConversationStateService conversationStateService) {
        this.conversationApplicationService = conversationApplicationService;
        this.conversationStateService = conversationStateService;
    }

    @PostMapping
    public ResponseEntity<DevConversationResponse> processarMensagem(@RequestBody DevConversationRequest request) {
        if (request == null || request.getNumber() == null || request.getMessage() == null) {
            return ResponseEntity.badRequest().build();
        }

        long startTime = System.currentTimeMillis();

        String reply = conversationApplicationService.processarMensagem(
            request.getNumber(),
            request.getMessage()
        );

        long processingTimeMs = System.currentTimeMillis() - startTime;

        DevConversationResponse response = buildResponse(request, reply, processingTimeMs);

        return ResponseEntity.ok(response);
    }

    private DevConversationResponse buildResponse(DevConversationRequest request, String reply, long processingTimeMs) {
        String conversationState = extractConversationState(request.getNumber());
        String customer = extractCustomer(request.getNumber());

        return DevConversationResponse.of(
            reply,
            null,
            conversationState,
            customer,
            processingTimeMs
        );
    }

    private String extractConversationState(String numero) {
        try {
            return conversationStateService.buscarPorNumero(numero)
                .getStep()
                .name();
        } catch (Exception e) {
            return "INICIO";
        }
    }

    private String extractCustomer(String numero) {
        try {
            return conversationStateService.buscarPorNumero(numero)
                .getNome();
        } catch (Exception e) {
            return null;
        }
    }
}
