package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.conversation.ConversationMessageProcessor;
import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationPipelineTest {

    @Test
    void devePararNoStepDeSaudacaoSemChamarFluxoLegado() {
        RecordingProcessor processor = new RecordingProcessor("legado");
        ConversationPipeline pipeline = pipelineComIntent(IntentType.GREETING, processor);

        String resposta = pipeline.processar("5511999999999", "Oi");

        assertEquals("Ola! Como posso ajudar voce hoje?", resposta);
        assertEquals(0, processor.calls);
    }

    @Test
    void deveEncaminharMensagemNaoTratadaParaFluxoLegado() {
        RecordingProcessor processor = new RecordingProcessor("Resposta do fluxo atual");
        ConversationPipeline pipeline = pipelineComIntent(IntentType.BOOK_APPOINTMENT, processor);

        String resposta = pipeline.processar("5511999999999", "Quero agendar unha segunda às 13");

        assertEquals("Resposta do fluxo atual", resposta);
        assertEquals(1, processor.calls);
        assertEquals("5511999999999", processor.numeroRecebido);
        assertEquals("Quero agendar unha segunda às 13", processor.mensagemRecebida);
    }

    private ConversationPipeline pipelineComIntent(IntentType intentType, ConversationMessageProcessor processor) {
        IntentEngine intentEngine = message -> new IntentResult(intentType);
        return new ConversationPipeline(List.of(
            new IntentDetectionStep(intentEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new ContextStep(),
            new FlowDispatcherStep(),
            new GreetingResponseStep(new ResponseBuilder()),
            new LegacyConversationProcessorStep(processor)
        ));
    }

    private static class RecordingProcessor implements ConversationMessageProcessor {
        private final String resposta;
        private int calls;
        private String numeroRecebido;
        private String mensagemRecebida;

        private RecordingProcessor(String resposta) {
            this.resposta = resposta;
        }

        @Override
        public String gerarResposta(String numero, String mensagem) {
            calls++;
            numeroRecebido = numero;
            mensagemRecebida = mensagem;
            return resposta;
        }
    }
}
