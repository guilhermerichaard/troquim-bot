package com.troquim_bot.application.conversation.engine;

/**
 * Step de contexto do pipeline existente.
 *
 * Reutiliza o {@link ConversationEngineContext} já existente (não cria
 * nenhum Context ou Pipeline novo). Nesta versão é um passo neutro
 * (pass-through): não chama {@link ConversationEngineContext#responder(String)}
 * e não altera numero, mensagem, intentResult ou entities já definidos pelos
 * steps anteriores (IntentDetectionStep / EntityExtractionStep).
 *
 * Por não finalizar o contexto nem modificar nada nele, sua presença no
 * pipeline não muda o resultado de nenhum step seguinte (GreetingResponseStep,
 * LegacyConversationProcessorStep) nem, portanto, o comportamento do
 * ConversationService acionado pelo fluxo legado.
 *
 * Serve como ponto único de extensão para, futuramente, enriquecer o
 * ConversationEngineContext existente com dados adicionais de contexto
 * (ex.: histórico da conversa, perfil do cliente), sem duplicar abstrações.
 */
public class ContextStep implements ConversationPipelineStep {

    @Override
    public void execute(ConversationEngineContext context) {
        // Passo neutro: nenhuma decisão é tomada e nenhum dado é alterado.
        // Ponto de extensão reservado para enriquecimento futuro do
        // ConversationEngineContext existente.
    }
}
