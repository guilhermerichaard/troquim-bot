package com.troquim_bot.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interface Spring Data JPA para ConversationStateJpaEntity.
 * 
 * Camada de infraestrutura pura. Não deve ser usada diretamente
 * pelo domínio — usar JpaConversationStateRepository (que implementa ConversationStateRepository).
 */
@Repository
public interface SpringDataConversationStateRepository extends JpaRepository<ConversationStateJpaEntity, String> {
}