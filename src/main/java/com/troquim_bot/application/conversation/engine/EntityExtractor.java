package com.troquim_bot.application.conversation.engine;

public interface EntityExtractor {
    ExtractedEntities extract(String message);
}
