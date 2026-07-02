package com.troquim_bot.conversation;

import com.troquim_bot.ai.config.AiConfiguration;
import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfileService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationServiceCustomerProfileTest {

    @Test
    void salvaNomeEUsaPerfilEmAtendimentosFuturos() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        ConversationService conversationService = new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                new ConversationStateService(),
                new ConversationMemory(),
                new OllamaService(new AiConfiguration()),
                new PromptService(),
                customerProfileService
        );

        String numero = "5511999999999";

        assertEquals("Boa tarde! Como posso ajudar?", conversationService.gerarResposta(numero, "Oi"));
        conversationService.gerarResposta(numero, "Meu nome é Guilherme.");

        assertEquals("Guilherme", customerProfileService.localizarPorTelefone(numero).orElseThrow().getNome());
        assertEquals("Boa tarde, Guilherme! Como posso ajudar?", conversationService.gerarResposta(numero, "Oi"));
        assertEquals("Seu nome está salvo como Guilherme.", conversationService.gerarResposta(numero, "Qual meu nome?"));
    }

    @Test
    void naoPerguntaNomeNovamenteQuandoPerfilJaTemNome() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        ConversationService conversationService = new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                new ConversationStateService(),
                new ConversationMemory(),
                new OllamaService(new AiConfiguration()),
                new PromptService(),
                customerProfileService
        );

        String numero = "5511888888888";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "Oi");
        conversationService.gerarResposta(numero, "quero manicure");
        conversationService.gerarResposta(numero, "sexta");
        String resposta = conversationService.gerarResposta(numero, "16h");

        assertEquals("Perfeito, Guilherme. Recebi sua solicitação para manicure na sexta às 16h. Vou verificar a disponibilidade e retorno com a confirmação.", resposta);
    }
}
