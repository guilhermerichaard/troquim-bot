package com.troquim_bot.application.messaging;

/**
 * Opcao de apresentacao interativa. O id e' um comando de interface estavel; a
 * Conversation continua sendo a autoridade sobre o significado do comando.
 */
public record OutboundInteractiveOption(String id, String title, String description) {

    public OutboundInteractiveOption {
        if (id == null || id.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("Interactive option exige id e title");
        }
        description = description == null ? "" : description;
    }

    public OutboundInteractiveOption(String id, String title) {
        this(id, title, "");
    }
}
