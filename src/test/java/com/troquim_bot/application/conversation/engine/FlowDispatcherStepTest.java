package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlowDispatcherStepTest {

    private final FlowDispatcherStep step = new FlowDispatcherStep();

    @Test
    void deveDefinirFluxoGreetingSemResponder() {
        ConversationEngineContext context = new ConversationEngineContext("5511999999999", "Oi");
        context.definirIntentResult(new IntentResult(IntentType.GREETING));

        step.execute(context);

        assertEquals(ConversationFlow.GREETING, context.fluxo());
        assertFalse(context.finalizado());
        assertNull(context.resposta());
    }

    @Test
    void deveDefinirFluxoLegacyParaIntencaoNaoGreeting() {
        ConversationEngineContext context = new ConversationEngineContext("5511999999999", "Quero agendar");
        context.definirIntentResult(new IntentResult(IntentType.BOOK_APPOINTMENT));

        step.execute(context);

        assertEquals(ConversationFlow.LEGACY, context.fluxo());
        assertFalse(context.finalizado());
        assertNull(context.resposta());
    }

    @Test
    void deveDefinirFluxoLegacyQuandoIntentAusente() {
        ConversationEngineContext context = new ConversationEngineContext("5511999999999", "xyz");

        step.execute(context);

        assertEquals(ConversationFlow.LEGACY, context.fluxo());
        assertFalse(context.finalizado());
        assertNull(context.resposta());
    }
}
