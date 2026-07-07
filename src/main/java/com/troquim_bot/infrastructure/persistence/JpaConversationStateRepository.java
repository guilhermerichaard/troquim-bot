package com.troquim_bot.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.repository.ConversationStateRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Adapter JPA que implementa ConversationStateRepository.
 * 
 * Serializa o ConversationState como JSON para persistência.
 * 
 * Anotado com @Primary para ser usado pelo Spring por padrão.
 */
@Repository
@Primary
public class JpaConversationStateRepository implements ConversationStateRepository {

    private final SpringDataConversationStateRepository springDataRepository;
    private final ObjectMapper objectMapper;

    public JpaConversationStateRepository(SpringDataConversationStateRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ConversationState save(ConversationState state) {
        if (state == null) {
            throw new IllegalArgumentException("ConversationState não pode ser nulo");
        }

        String json = toJson(state);
        LocalDateTime agora = LocalDateTime.now();

        ConversationStateJpaEntity entity = springDataRepository.findById(state.getNumero())
                .map(existing -> {
                    existing.setStateJson(json);
                    existing.setAtualizadoEm(agora);
                    return existing;
                })
                .orElseGet(() -> new ConversationStateJpaEntity(
                        state.getNumero(), json, agora, agora
                ));

        springDataRepository.save(entity);
        return state;
    }

    @Override
    public ConversationState findByNumero(String numero) {
        if (numero == null) {
            return null;
        }
        return springDataRepository.findById(numero)
                .map(entity -> fromJson(entity.getStateJson(), entity.getNumero()))
                .orElse(null);
    }

    @Override
    public boolean existsByNumero(String numero) {
        if (numero == null) {
            return false;
        }
        return springDataRepository.existsById(numero);
    }

    @Override
    public void deleteByNumero(String numero) {
        if (numero != null) {
            springDataRepository.deleteById(numero);
        }
    }

    // ==================== SERIALIZAÇÃO ====================

    private String toJson(ConversationState state) {
        try {
            ConversationStateSnapshot snapshot = ConversationStateSnapshot.fromDomain(state);
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar ConversationState", e);
        }
    }

    private ConversationState fromJson(String json, String numero) {
        try {
            ConversationStateSnapshot snapshot = objectMapper.readValue(json, ConversationStateSnapshot.class);
            return snapshot.toDomain(numero);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao desserializar ConversationState", e);
        }
    }
}