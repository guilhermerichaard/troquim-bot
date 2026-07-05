package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.conversation.ConversationMessageProcessor;

public class LegacyConversationProcessorStep implements ConversationPipelineStep {

    private final ConversationMessageProcessor conversationMessageProcessor;

    public LegacyConversationProcessorStep(ConversationMessageProcessor conversationMessageProcessor) {
        if (conversationMessageProcessor == null) {
            throw new IllegalArgumentException("ConversationMessageProcessor e obrigatorio");
        }
        this.conversationMessageProcessor = conversationMessageProcessor;
    }

    @Override
    public void execute(ConversationEngineContext context) {
        context.responder(conversationMessageProcessor.gerarResposta(context.numero(), context.mensagem()));
    }
}
