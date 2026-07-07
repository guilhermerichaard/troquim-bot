package com.troquim_bot.application.conversation;

import com.troquim_bot.application.conversation.engine.ConversationPipeline;
import com.troquim_bot.application.conversation.engine.DefaultEntityExtractor;
import com.troquim_bot.application.conversation.engine.EntityExtractionStep;
import com.troquim_bot.application.conversation.engine.FlowDispatcherStep;
import com.troquim_bot.application.conversation.engine.IntentDetectionStep;
import com.troquim_bot.application.conversation.engine.LegacyConversationProcessorStep;
import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import com.troquim_bot.application.intent.RuleBasedIntentEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testes de regressão para garantir que o RuleBasedIntentEngine (V2) é a
 * única fonte de verdade da intenção, e que o ConversationService legado
 * NÃO reclassifica a intenção quando ela já veio do pipeline V2.
 *
 * Cenários de bug cobertos:
 * 1. "Cancelar agendamento" → NÃO deve virar AGENDAMENTO (booking flow)
 * 2. "Serviços disponíveis" → NÃO deve cair em SAUDACAO
 * 3. "Dias disponíveis" → NÃO deve perder contexto
 */
class IntentDuplicationRegressionTest {

    private final IntentEngine ruleBasedEngine = new RuleBasedIntentEngine();

    @Test
    void cancelarAgendamentoNaoDeveVirarAgendamento() {
        IntentResult result = ruleBasedEngine.classify("Quero cancelar meu agendamento");
        assertEquals(IntentType.CANCEL_APPOINTMENT, result.type(),
            "RuleBasedIntentEngine deve classificar 'cancelar agendamento' como CANCEL_APPOINTMENT");
    }

    @Test
    void cancelarAgendamentoNaoDeveIniciarFluxoDeBooking() {
        // Simula o pipeline completo: a intenção V2 (CANCEL_APPOINTMENT) deve
        // ser passada para o LegacyConversationProcessorStep, que por sua vez
        // deve repassá-la ao ConversationService sem reclassificar.
        AtomicReference<IntentType> intentRecebida = new AtomicReference<>(null);

        ConversationMessageProcessor processor = new ConversationMessageProcessor() {
            @Override
            public String gerarResposta(String numero, String mensagem) {
                return "fallback";
            }

            @Override
            public String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
                intentRecebida.set(v2IntentType);
                return "resposta para cancelamento";
            }
        };

        ConversationPipeline pipeline = new ConversationPipeline(List.of(
            new IntentDetectionStep(ruleBasedEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new FlowDispatcherStep(),
            new LegacyConversationProcessorStep(processor)
        ));

        String resposta = pipeline.processar("5511000000001", "Quero cancelar meu agendamento");

        assertNotNull(intentRecebida.get(), "A intenção V2 deve ser passada para o processor");
        assertEquals(IntentType.CANCEL_APPOINTMENT, intentRecebida.get(),
            "A intenção CANCEL_APPOINTMENT deve chegar ao processor, não AGENDAMENTO");
        assertEquals("resposta para cancelamento", resposta);
    }

    @Test
    void servicosDisponiveisNaoDeveCairEmSaudacao() {
        IntentResult result = ruleBasedEngine.classify("Quais serviços disponíveis?");
        assertEquals(IntentType.ASK_SERVICES, result.type(),
            "RuleBasedIntentEngine deve classificar 'serviços disponíveis' como ASK_SERVICES, não GREETING");
    }

    @Test
    void servicosDisponiveisDevePassarIntencaoCorretaParaProcessor() {
        AtomicReference<IntentType> intentRecebida = new AtomicReference<>(null);

        ConversationMessageProcessor processor = new ConversationMessageProcessor() {
            @Override
            public String gerarResposta(String numero, String mensagem) {
                return "fallback";
            }

            @Override
            public String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
                intentRecebida.set(v2IntentType);
                return "resposta para servicos";
            }
        };

        ConversationPipeline pipeline = new ConversationPipeline(List.of(
            new IntentDetectionStep(ruleBasedEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new FlowDispatcherStep(),
            new LegacyConversationProcessorStep(processor)
        ));

        pipeline.processar("5511000000002", "Quais serviços disponíveis?");

        assertNotNull(intentRecebida.get(), "A intenção V2 deve ser passada para o processor");
        assertEquals(IntentType.ASK_SERVICES, intentRecebida.get(),
            "ASK_SERVICES deve chegar ao processor, não GREETING");
    }

    @Test
    void consultaDisponibilidadeNaoDevePerderContexto() {
        IntentResult result = ruleBasedEngine.classify("tem horário disponível?");
        assertEquals(IntentType.CHECK_AVAILABILITY, result.type(),
            "RuleBasedIntentEngine deve classificar 'tem horário disponível' como CHECK_AVAILABILITY");
    }

    @Test
    void consultaDisponibilidadeDevePassarIntencaoCorretaParaProcessor() {
        AtomicReference<IntentType> intentRecebida = new AtomicReference<>(null);

        ConversationMessageProcessor processor = new ConversationMessageProcessor() {
            @Override
            public String gerarResposta(String numero, String mensagem) {
                return "fallback";
            }

            @Override
            public String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
                intentRecebida.set(v2IntentType);
                return "resposta para disponibilidade";
            }
        };

        ConversationPipeline pipeline = new ConversationPipeline(List.of(
            new IntentDetectionStep(ruleBasedEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new FlowDispatcherStep(),
            new LegacyConversationProcessorStep(processor)
        ));

        pipeline.processar("5511000000003", "tem horário disponível?");

        assertNotNull(intentRecebida.get(), "A intenção V2 deve ser passada para o processor");
        assertEquals(IntentType.CHECK_AVAILABILITY, intentRecebida.get(),
            "CHECK_AVAILABILITY deve chegar ao processor");
    }

    @Test
    void legacyConversationProcessorStepPassaV2IntentQuandoDisponivel() {
        // Testa que o LegacyConversationProcessorStep passa a intenção V2
        // quando o contexto tem intentResult definido
        AtomicReference<IntentType> intentRecebida = new AtomicReference<>(null);

        ConversationMessageProcessor processor = new ConversationMessageProcessor() {
            @Override
            public String gerarResposta(String numero, String mensagem) {
                intentRecebida.set(null); // marca que NÃO recebeu V2 intent
                return "sem v2";
            }

            @Override
            public String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
                intentRecebida.set(v2IntentType);
                return "com v2";
            }
        };

        ConversationPipeline pipeline = new ConversationPipeline(List.of(
            new IntentDetectionStep(ruleBasedEngine),
            new EntityExtractionStep(new DefaultEntityExtractor()),
            new FlowDispatcherStep(),
            new LegacyConversationProcessorStep(processor)
        ));

        String resposta = pipeline.processar("5511000000004", "Quero agendar unha");

        assertEquals("com v2", resposta,
            "Deve usar o método com v2IntentType quando intentResult está disponível");
        assertEquals(IntentType.BOOK_APPOINTMENT, intentRecebida.get(),
            "BOOK_APPOINTMENT deve ser passado para o processor");
    }

    @Test
    void legacyConversationProcessorStepFallbackQuandoSemIntentResult() {
        // Testa que o LegacyConversationProcessorStep usa o método sem V2 intent
        // quando o contexto NÃO tem intentResult (fallback)
        AtomicReference<Boolean> chamouComV2 = new AtomicReference<>(false);

        ConversationMessageProcessor processor = new ConversationMessageProcessor() {
            @Override
            public String gerarResposta(String numero, String mensagem) {
                return "fallback sem v2";
            }

            @Override
            public String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
                chamouComV2.set(true);
                return "com v2";
            }
        };

        // Pipeline sem IntentDetectionStep para simular ausência de intentResult
        ConversationPipeline pipeline = new ConversationPipeline(List.of(
            new LegacyConversationProcessorStep(processor)
        ));

        String resposta = pipeline.processar("5511000000005", "teste");

        assertEquals("fallback sem v2", resposta,
            "Deve usar o método sem v2IntentType quando intentResult é null");
    }
}