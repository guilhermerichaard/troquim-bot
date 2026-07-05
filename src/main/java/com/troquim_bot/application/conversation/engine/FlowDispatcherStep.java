package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;

public class FlowDispatcherStep implements ConversationPipelineStep {

    @Override
    public void execute(ConversationEngineContext context) {
        IntentResult intentResult = context.intentResult();
        if (intentResult != null && intentResult.type() == IntentType.GREETING) {
            context.definirFluxo(ConversationFlow.GREETING);
            return;
        }

        context.definirFluxo(ConversationFlow.LEGACY);
    }
}
