package com.troquim_bot.conversation;

import com.troquim_bot.ai.config.AiConfiguration;
import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.AppointmentStatus;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationServiceCustomerProfileTest {

    @Test
    void salvaNomeEUsaPerfilEmAtendimentosFuturos() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                new AppointmentService()
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
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                new AppointmentService()
        );

        String numero = "5511888888888";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "Oi");
        conversationService.gerarResposta(numero, "quero manicure");
        conversationService.gerarResposta(numero, "sexta");
        String resposta = conversationService.gerarResposta(numero, "16h");

        assertEquals("Perfeito, Guilherme. Recebi sua solicitação para manicure na sexta às 16h. Vou verificar a disponibilidade e retorno com a confirmação.", resposta);
    }

    @Test
    void estadoPendenteNaoMonopolizaNovasIntencoes() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ConversationService conversationService = criarConversationService(customerProfileService, appointmentService);

        String numero = "5511666666666";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        conversationService.gerarResposta(numero, "15h");

        assertEquals("Boa tarde, Guilherme! Como posso ajudar?", conversationService.gerarResposta(numero, "Oi"));
        assertEquals("Seu nome está salvo como Guilherme.", conversationService.gerarResposta(numero, "Qual meu nome?"));
        assertEquals("Lembro sim, Guilherme. Como posso ajudar?", conversationService.gerarResposta(numero, "Lembra de mim?"));
        assertEquals(AppointmentStatus.PENDENTE,
                appointmentService.buscarUltimoAgendamentoPorTelefone(numero).orElseThrow().getStatus());
        assertEquals("Sua solicitação para unha na segunda às 15h está com status PENDENTE.",
                conversationService.gerarResposta(numero, "Agendou mesmo?"));
    }

    @Test
    void criaAppointmentPendenteQuandoFluxoFicaCompleto() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ConversationService conversationService = criarConversationService(customerProfileService, appointmentService);

        String numero = "5511555555555";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "terça");
        conversationService.gerarResposta(numero, "15h");

        assertEquals(1, appointmentService.listarAgendamentosDoCliente(numero).size());
        assertEquals("Sua solicitação para unha na terça às 15h está com status PENDENTE.",
                conversationService.gerarResposta(numero, "qual meu agendamento?"));
        assertEquals("Sua solicitação para unha na terça às 15h está com status PENDENTE.",
                conversationService.gerarResposta(numero, "qual horário?"));
        assertEquals("Sua solicitação para unha na terça às 15h está com status PENDENTE.",
                conversationService.gerarResposta(numero, "qual serviço?"));
        assertEquals("Sua solicitação para unha na terça às 15h está com status PENDENTE.",
                conversationService.gerarResposta(numero, "marquei para quando?"));
    }

    private ConversationService criarConversationService(CustomerProfileService customerProfileService,
                                                        AppointmentService appointmentService) {
        return new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                new ConversationStateService(),
                new ConversationMemory(),
                new OllamaService(new AiConfiguration()),
                new PromptService(),
                customerProfileService,
                appointmentService,
                new AppointmentBookingService(new ScheduleService(), appointmentService)
        );
    }
}
