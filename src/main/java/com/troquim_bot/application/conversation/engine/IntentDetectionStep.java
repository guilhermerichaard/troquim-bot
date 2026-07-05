package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.intent.IntentEngine;

public class IntentDetectionStep implements ConversationPipelineStep {

    private final IntentEngine intentEngine;

    public IntentDetectionStep(IntentEngine intentEngine) {
        if (intentEngine == null) {
            throw new IllegalArgumentException("IntentEngine e obrigatorio");
        }
        this.intentEngine = intentEngine;
    }

    @Override
    public void execute(ConversationEngineContext context) {
        context.definirIntentResult(intentEngine.classify(context.mensagem()));
    }
}
