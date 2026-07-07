package com.troquim_bot.repository;

import com.troquim_bot.conversation.state.ConversationState;

/**
 * Repository abstraction para persistência de ConversationState.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface ConversationStateRepository {

    /**
     * Salva um ConversationState (cria ou atualiza).
     */
    ConversationState save(ConversationState state);

    /**
     * Busca um ConversationState pelo número do telefone.
     * 
     * @return ConversationState se encontrado, null caso contrário
     */
    ConversationState findByNumero(String numero);

    /**
     * Verifica se existe um ConversationState com o número informado.
     */
    boolean existsByNumero(String numero);

    /**
     * Remove um ConversationState por número.
     */
    void deleteByNumero(String numero);
}