package com.troquim_bot.application.conversation.engine;

public class EntityExtractionStep implements ConversationPipelineStep {

    private final EntityExtractor entityExtractor;

    public EntityExtractionStep(EntityExtractor entityExtractor) {
        if (entityExtractor == null) {
            throw new IllegalArgumentException("EntityExtractor e obrigatorio");
        }
        this.entityExtractor = entityExtractor;
    }

    @Override
    public void execute(ConversationEngineContext context) {
        context.definirEntities(entityExtractor.extract(context.mensagem()));
    }
}
