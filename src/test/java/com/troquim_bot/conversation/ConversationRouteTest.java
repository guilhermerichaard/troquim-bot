package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationRouteTest {

    @Test
    void deveCriarRotaComResultadoDoConversationStateService() {
        ConversationState state = new ConversationState("5511999999999");
        RecordingConversationStateService conversationStateService = new RecordingConversationStateService(true);

        ConversationRoute route = ConversationRoute.rotear(
                conversationStateService,
                state,
                "quero agendar unha",
                IntentType.AGENDAMENTO
        );

        assertEquals(IntentType.AGENDAMENTO, route.intentType());
        assertTrue(route.continuaFluxo());
        assertSame(state, conversationStateService.stateRecebido);
        assertEquals("quero agendar unha", conversationStateService.mensagemRecebida);
        assertEquals(IntentType.AGENDAMENTO, conversationStateService.intentTypeRecebido);
    }

    private static class RecordingConversationStateService extends ConversationStateService {
        private final boolean deveContinuarFluxo;
        private ConversationState stateRecebido;
        private String mensagemRecebida;
        private IntentType intentTypeRecebido;

        private RecordingConversationStateService(boolean deveContinuarFluxo) {
            this.deveContinuarFluxo = deveContinuarFluxo;
        }

        @Override
        public boolean deveContinuarFluxo(ConversationState state, String mensagem, IntentType intentType) {
            stateRecebido = state;
            mensagemRecebida = mensagem;
            intentTypeRecebido = intentType;
            return deveContinuarFluxo;
        }
    }
}
