package com.troquim_bot.ai.memory;

import java.time.LocalDateTime;

public class ConversationMessage {

    private final String role;
    private final String content;
    private final LocalDateTime timestamp;

    public ConversationMessage(String role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
