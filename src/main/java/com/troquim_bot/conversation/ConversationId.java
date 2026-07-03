package com.troquim_bot.conversation;

import java.util.UUID;

/**
 * Value Object que representa o identificador único de uma Conversation.
 * Imutável e validado na criação.
 */
public class ConversationId {

    private final UUID value;

    public ConversationId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ConversationId não pode ser nulo");
        }
        this.value = value;
    }

    public static ConversationId from(UUID value) {
        return new ConversationId(value);
    }

    public static ConversationId generate() {
        return new ConversationId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationId that = (ConversationId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}