package com.troquim_bot.application.conversation;

import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;
import com.troquim_bot.conversation.ConversationService;
import com.troquim_bot.repository.ConversationRepository;
import com.troquim_bot.repository.InMemoryConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final ConversationService legacyConversationService;

    public ConversationApplicationService() {
        this(new InMemoryConversationRepository(), null);
    }

    @Autowired
    public ConversationApplicationService(ConversationService conversationService) {
        this(new InMemoryConversationRepository(), conversationService);
    }

    public ConversationApplicationService(ConversationRepository conversationRepository) {
        this(conversationRepository, null);
    }

    ConversationApplicationService(ConversationRepository conversationRepository,
                                   ConversationService legacyConversationService) {
        if (conversationRepository == null) {
            throw new IllegalArgumentException("ConversationRepository e obrigatorio");
        }
        this.conversationRepository = conversationRepository;
        this.legacyConversationService = legacyConversationService;
    }

    public Conversation criarConversa(String customerId) {
        String normalizedCustomerId = normalizeUuid(customerId, "CustomerId");
        Conversation conversation = new Conversation(ConversationId.generate(), normalizedCustomerId);
        return conversationRepository.save(conversation);
    }

    public Optional<Conversation> buscarPorId(ConversationId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationRepository.findById(id));
    }

    public List<Conversation> listarTodos() {
        return conversationRepository.findAll();
    }

    public Conversation atualizarCampos(ConversationId id,
                                        String selectedServiceId,
                                        String selectedProfessionalId,
                                        String selectedDate,
                                        String selectedStartTime,
                                        String selectedEndTime,
                                        String reservationId,
                                        String appointmentId) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.atualizarCampos(
            normalizeOptionalUuid(selectedServiceId, "SelectedServiceId"),
            normalizeOptionalUuid(selectedProfessionalId, "SelectedProfessionalId"),
            parseOptionalDate(selectedDate, "SelectedDate"),
            parseOptionalTime(selectedStartTime, "SelectedStartTime"),
            parseOptionalTime(selectedEndTime, "SelectedEndTime"),
            normalizeOptionalUuid(reservationId, "ReservationId"),
            normalizeOptionalUuid(appointmentId, "AppointmentId")
        );
        return conversationRepository.save(conversation);
    }

    public Conversation avancarEtapa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.avancar();
        return conversationRepository.save(conversation);
    }

    public Conversation voltarEtapa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.voltar();
        return conversationRepository.save(conversation);
    }

    public Conversation resetarConversa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.resetar();
        return conversationRepository.save(conversation);
    }

    public Conversation cancelarConversa(ConversationId id) {
        Conversation conversation = getConversationOrThrow(id);
        conversation.cancelar();
        return conversationRepository.save(conversation);
    }

    public boolean existe(ConversationId id) {
        if (id == null) {
            return false;
        }
        return conversationRepository.exists(id);
    }

    public String processarMensagem(String numero, String mensagem) {
        if (legacyConversationService == null) {
            throw new IllegalStateException("Processamento de mensagens nao faz parte do Conversation Engine MVP");
        }
        return legacyConversationService.gerarResposta(numero, mensagem);
    }

    private Conversation getConversationOrThrow(ConversationId id) {
        Conversation conversation = conversationRepository.findById(id);
        if (conversation == null) {
            throw new NoSuchElementException("Conversation nao encontrada");
        }
        return conversation;
    }

    private String normalizeUuid(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " e obrigatorio");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " deve ser um UUID valido", e);
        }
    }

    private String normalizeOptionalUuid(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.trim().isEmpty()) {
            return "";
        }
        return normalizeUuid(value, fieldName);
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " deve estar no formato yyyy-MM-dd", e);
        }
    }

    private LocalTime parseOptionalTime(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " deve estar no formato HH:mm", e);
        }
    }
}
