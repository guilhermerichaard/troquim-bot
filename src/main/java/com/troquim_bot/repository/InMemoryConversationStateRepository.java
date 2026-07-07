package com.troquim_bot.repository;

import com.troquim_bot.conversation.state.ConversationState;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do ConversationStateRepository.
 * Usada em conjunto com InMemory* repositories para testes.
 */
@Repository
public class InMemoryConversationStateRepository implements ConversationStateRepository {

    private final ConcurrentMap<String, ConversationState> states = new ConcurrentHashMap<>();

    @Override
    public ConversationState save(ConversationState state) {
        if (state == null) {
            throw new IllegalArgumentException("ConversationState não pode ser nulo");
        }
        states.put(state.getNumero(), state);
        return state;
    }

    @Override
    public ConversationState findByNumero(String numero) {
        if (numero == null) {
            return null;
        }
        return states.get(numero);
    }

    @Override
    public boolean existsByNumero(String numero) {
        if (numero == null) {
            return false;
        }
        return states.containsKey(numero);
    }

    @Override
    public void deleteByNumero(String numero) {
        if (numero != null) {
            states.remove(numero);
        }
    }
}