package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.intent.IntentType;

public class GreetingResponseStep implements ConversationPipelineStep {

    private final ResponseBuilder responseBuilder;

    public GreetingResponseStep(ResponseBuilder responseBuilder) {
        if (responseBuilder == null) {
            throw new IllegalArgumentException("ResponseBuilder e obrigatorio");
        }
        this.responseBuilder = responseBuilder;
    }

    @Override
    public void execute(ConversationEngineContext context) {
        if (context.intentResult() != null && context.intentResult().type() == IntentType.GREETING) {
            context.responder(responseBuilder.greeting());
        }
    }
}
