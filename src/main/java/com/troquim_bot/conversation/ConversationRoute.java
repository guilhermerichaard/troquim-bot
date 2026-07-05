package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;

record ConversationRoute(IntentType intentType, boolean continuaFluxo) {

    static ConversationRoute rotear(ConversationStateService conversationStateService,
                                    ConversationState conversationState,
                                    String mensagem,
                                    IntentType intentType) {
        boolean continuaFluxo = conversationStateService.deveContinuarFluxo(
                conversationState,
                mensagem,
                intentType
        );
        return new ConversationRoute(intentType, continuaFluxo);
    }
}
