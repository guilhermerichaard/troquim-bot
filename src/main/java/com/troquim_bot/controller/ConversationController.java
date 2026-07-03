package com.troquim_bot.controller;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.controller.dto.ConversationResponse;
import com.troquim_bot.controller.dto.CreateConversationRequest;
import com.troquim_bot.controller.dto.UpdateConversationRequest;
import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;

    public ConversationController(ConversationApplicationService conversationApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> listarTodos() {
        List<ConversationResponse> response = conversationApplicationService.listarTodos().stream()
            .map(ConversationResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> buscarPorId(@PathVariable String id) {
        try {
            ConversationId conversationId = parseId(id);
            return conversationApplicationService.buscarPorId(conversationId)
                .map(ConversationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> criar(@RequestBody CreateConversationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Conversation conversation = conversationApplicationService.criarConversa(request.getCustomerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ConversationResponse.from(conversation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationResponse> atualizar(@PathVariable String id,
                                                          @RequestBody UpdateConversationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Conversation conversation = conversationApplicationService.atualizarCampos(
                parseId(id),
                request.getSelectedServiceId(),
                request.getSelectedProfessionalId(),
                request.getSelectedDate(),
                request.getSelectedStartTime(),
                request.getSelectedEndTime(),
                request.getReservationId(),
                request.getAppointmentId()
            );
            return ResponseEntity.ok(ConversationResponse.from(conversation));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/next")
    public ResponseEntity<ConversationResponse> avancar(@PathVariable String id) {
        try {
            Conversation conversation = conversationApplicationService.avancarEtapa(parseId(id));
            return ResponseEntity.ok(ConversationResponse.from(conversation));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/back")
    public ResponseEntity<ConversationResponse> voltar(@PathVariable String id) {
        try {
            Conversation conversation = conversationApplicationService.voltarEtapa(parseId(id));
            return ResponseEntity.ok(ConversationResponse.from(conversation));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/reset")
    public ResponseEntity<ConversationResponse> resetar(@PathVariable String id) {
        try {
            Conversation conversation = conversationApplicationService.resetarConversa(parseId(id));
            return ResponseEntity.ok(ConversationResponse.from(conversation));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable String id) {
        try {
            conversationApplicationService.cancelarConversa(parseId(id));
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private ConversationId parseId(String id) {
        return ConversationId.from(UUID.fromString(id));
    }
}
