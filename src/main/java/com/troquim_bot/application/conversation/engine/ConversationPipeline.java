package com.troquim_bot.application.conversation.engine;

import java.util.List;

public class ConversationPipeline {

    private final List<ConversationPipelineStep> steps;

    public ConversationPipeline(List<ConversationPipelineStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("ConversationPipeline precisa de ao menos um step");
        }
        this.steps = List.copyOf(steps);
    }

    public String processar(String numero, String mensagem) {
        ConversationEngineContext context = new ConversationEngineContext(numero, mensagem);

        for (ConversationPipelineStep step : steps) {
            if (context.finalizado()) {
                break;
            }
            step.execute(context);
        }

        if (context.resposta() == null || context.resposta().isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        return context.resposta();
    }
}
