package com.troquim_bot.application.conversation;

import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationApplicationService {

    private final ConversationRegistry conversationRegistry;
    private final ConversationOrchestrator conversationOrchestrator;
    private final ConversationInputMapper conversationInputMapper;

    public ConversationApplicationService(ConversationRegistry conversationRegistry,
                                          ConversationOrchestrator conversationOrchestrator,
                                          ConversationInputMapper conversationInputMapper) {
        if (conversationRegistry == null) {
            throw new IllegalArgumentException("ConversationRegistry e obrigatorio");
        }
        if (conversationOrchestrator == null) {
            throw new IllegalArgumentException("ConversationOrchestrator e obrigatorio");
        }
        if (conversationInputMapper == null) {
            throw new IllegalArgumentException("ConversationInputMapper e obrigatorio");
        }
        this.conversationRegistry = conversationRegistry;
        this.conversationOrchestrator = conversationOrchestrator;
        this.conversationInputMapper = conversationInputMapper;
    }

    public Conversation criarConversa(String customerId) {
        return conversationRegistry.criarConversa(conversationInputMapper.customerId(customerId));
    }

    public Optional<Conversation> buscarPorId(ConversationId id) {
        return conversationRegistry.buscarPorId(id);
    }

    public List<Conversation> listarTodos() {
        return conversationRegistry.listarTodos();
    }

    public Conversation atualizarCampos(ConversationId id,
                                        String selectedServiceId,
                                        String selectedProfessionalId,
                                        String selectedDate,
                                        String selectedStartTime,
                                        String selectedEndTime,
                                        String reservationId,
                                        String appointmentId) {
        ConversationInputMapper.ConversationUpdate update = conversationInputMapper.update(
            selectedServiceId,
            selectedProfessionalId,
            selectedDate,
            selectedStartTime,
            selectedEndTime,
            reservationId,
            appointmentId
        );

        return conversationRegistry.atualizarCampos(
            id,
            update
        );
    }

    public Conversation avancarEtapa(ConversationId id) {
        return conversationRegistry.avancarEtapa(id);
    }

    public Conversation voltarEtapa(ConversationId id) {
        return conversationRegistry.voltarEtapa(id);
    }

    public Conversation resetarConversa(ConversationId id) {
        return conversationRegistry.resetarConversa(id);
    }

    public Conversation cancelarConversa(ConversationId id) {
        return conversationRegistry.cancelarConversa(id);
    }

    public boolean existe(ConversationId id) {
        return conversationRegistry.existe(id);
    }

    public String processarMensagem(String numero, String mensagem) {
        return conversationOrchestrator.processarMensagem(numero, mensagem);
    }

    public void receberWebhookWhatsApp(String payload) throws Exception {
        conversationOrchestrator.receberWebhookWhatsApp(payload);
    }
}
