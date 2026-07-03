package com.troquim_bot.repository;

import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;

import java.util.List;

public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Conversation findById(ConversationId id);

    List<Conversation> findAll();

    boolean exists(ConversationId id);

    void delete(ConversationId id);
}
