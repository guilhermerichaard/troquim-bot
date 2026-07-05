package com.troquim_bot.application.conversation.engine;

public interface ConversationPipelineStep {
    void execute(ConversationEngineContext context);
}
