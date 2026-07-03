package com.troquim_bot.repository;

import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryConversationRepository implements ConversationRepository {

    private final ConcurrentMap<ConversationId, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation nao pode ser nula");
        }
        conversations.put(conversation.getId(), conversation);
        return conversation;
    }

    @Override
    public Conversation findById(ConversationId id) {
        if (id == null) {
            return null;
        }
        return conversations.get(id);
    }

    @Override
    public List<Conversation> findAll() {
        return new ArrayList<>(conversations.values());
    }

    @Override
    public boolean exists(ConversationId id) {
        if (id == null) {
            return false;
        }
        return conversations.containsKey(id);
    }

    @Override
    public void delete(ConversationId id) {
        if (id != null) {
            conversations.remove(id);
        }
    }
}
