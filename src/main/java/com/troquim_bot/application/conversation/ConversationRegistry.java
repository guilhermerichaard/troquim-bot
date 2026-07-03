package com.troquim_bot.application.conversation;

import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;
import com.troquim_bot.repository.ConversationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
public class ConversationRegistry {

    private final ConversationRepository conversationRepository;

    public ConversationRegistry(ConversationRepository conversationRepository) {
        if (conversationRepository == null) {
            throw new IllegalArgumentException("ConversationRepository e obrigatorio");
        }
        this.conversationRepository = conversationRepository;
    }

    Conversation criarConversa(String customerId) {
        Conversation conversation = new Conversation(ConversationId.generate(), customerId);
        return conversationRepository.save(conversation);
    }

    Optional<Conversation> buscarPorId(ConversationId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationRepository.findById(id));
    }

    List<Conversation> listarTodos() {
        return conversationRepository.findAll();
    }

    Conversation atualizarCampos(ConversationId id,
                                 ConversationInputMapper.ConversationUpdate update) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.atualizarCampos(
            update.selectedServiceId(),
            update.selectedProfessionalId(),
            update.selectedDate(),
            update.selectedStartTime(),
            update.selectedEndTime(),
            update.reservationId(),
            update.appointmentId()
        );
        return conversationRepository.save(conversation);
    }

    Conversation avancarEtapa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.avancar();
        return conversationRepository.save(conversation);
    }

    Conversation voltarEtapa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.voltar();
        return conversationRepository.save(conversation);
    }

    Conversation resetarConversa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.resetar();
        return conversationRepository.save(conversation);
    }

    Conversation cancelarConversa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.cancelar();
        return conversationRepository.save(conversation);
    }

    boolean existe(ConversationId id) {
        if (id == null) {
            return false;
        }
        return conversationRepository.exists(id);
    }

    private Conversation getConversationOrThrow(ConversationId id) {
        Conversation conversation = conversationRepository.findById(id);
        if (conversation == null) {
            throw new NoSuchElementException("Conversation nao encontrada");
        }
        return conversation;
    }
}
