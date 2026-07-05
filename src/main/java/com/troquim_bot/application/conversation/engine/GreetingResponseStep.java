package com.troquim_bot.application.conversation.engine;

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
        if (context.fluxo() == ConversationFlow.GREETING) {
            context.responder(responseBuilder.greeting());
        }
    }
}
