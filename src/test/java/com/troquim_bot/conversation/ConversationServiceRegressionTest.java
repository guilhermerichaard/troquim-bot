package com.troquim_bot.conversation;

import com.troquim_bot.ai.config.AiConfiguration;
import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationServiceRegressionTest {

    // Cenário 1: Sábado às 17h deve ser recusado
    @Test
    void sabado17HorasDeveSerRecusadoForaDoExpediente() {
        Fixture fixture = criarFixtureComNome("5511000000001", "Gui");

        fixture.conversationService.gerarResposta(fixture.numero, "Quero marcar unha sábado às 17");

        assertEquals(0, fixture.appointmentApplicationService.listarTodos().size());
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Quero marcar unha sábado às 17");
        assertEquals("Esse horário não está disponível. Qual outro horário você prefere?", resposta);
    }

    // Cenário 2: Mensagem neutra não reaproveita contexto antigo
    @Test
    void mensagemNeutraNaoReaproveitaContextoAntigo() {
        Fixture fixture = criarFixtureSemNome("5511000000002");

        fixture.conversationService.gerarResposta(fixture.numero, "unha sábado às 17");
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "tá bom");

        assertEquals(0, fixture.appointmentApplicationService.listarTodos().size());
        assertFalse(resposta.toLowerCase().contains("sábado"));
        assertFalse(resposta.toLowerCase().contains("17"));
    }

    // Cenário 3: "Já disse" não vira nome
    @Test
    void jaDiseNaoViraNome() {
        Fixture fixture = criarFixtureComNome("5511000000003", "Gui");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Já disse");

        assertEquals("Gui", fixture.customerProfileService.localizarPorTelefone(fixture.numero).orElseThrow().getNome());
        assertFalse(resposta.toLowerCase().contains("já disse"));
    }

    // Cenário 4: Consulta de disponibilidade não pode cair em horário de funcionamento
    @Test
    void consultaDisponibilidadeNaoRespondeApenasHorarioFuncionamento() {
        Fixture fixture = criarFixtureSemNome("5511000000004");

        String resposta1 = fixture.conversationService.gerarResposta(fixture.numero, "Segunda horários disponível");
        assertFalse(resposta1.toLowerCase().contains("funcionamos de segunda a sexta"));

        String resposta2 = fixture.conversationService.gerarResposta(fixture.numero, "Agenda segunda tem qual horário");
        assertFalse(resposta2.toLowerCase().contains("funcionamos de segunda a sexta"));

        String resposta3 = fixture.conversationService.gerarResposta(fixture.numero, "Horário terça");
        assertFalse(resposta3.toLowerCase().contains("funcionamos de segunda a sexta"));
    }

    // Cenário 5: Consulta de agendamento não pode ser confundida com disponibilidade
    @Test
    void consultaAgendamentoRetornaAppointmentRealNaoDisponibilidade() {
        Fixture fixture = criarFixtureComNome("5511000000005", "Gui");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta1 = fixture.conversationService.gerarResposta(fixture.numero, "Meu agendamento");
        assertEquals("Você tem um agendamento para unha na segunda às 13h.", resposta1);

        String resposta2 = fixture.conversationService.gerarResposta(fixture.numero, "Agendei?");
        assertEquals("Você tem um agendamento para unha na segunda às 13h.", resposta2);
    }

    // Cenário 6: Agendamento válido deve confirmar de verdade
    @Test
    void agendamentoValidoConfirmaDeVerdade() {
        Fixture fixture = criarFixtureComNome("5511000000006", "Gui");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Agendar unha terça 13");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());
        assertEquals("Perfeito, Gui. Seu horário para unha na terça às 13h foi reservado.", resposta);
        assertFalse(resposta.toLowerCase().contains("estou registrando sua solicitação"));
    }

    private Fixture criarConversationServiceCompleto(CustomerProfileService customerProfileService) {
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        AvailabilityApplicationService availabilityApplicationService = new AvailabilityApplicationService(
                new com.troquim_bot.repository.InMemoryAvailabilityRepository(),
                scheduleService
        );
        ConversationStateService conversationStateService = new ConversationStateService();

        ConversationService conversationService = new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                conversationStateService,
                new ConversationMemory(),
                new StubOllamaService(),
                new PromptService(),
                customerProfileService,
                appointmentApplicationService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService
                ),
                availabilityApplicationService
        );

        return new Fixture(
                null,
                conversationService,
                customerProfileService,
                appointmentApplicationService
        );
    }

    private Fixture criarFixtureComNome(String numero, String nome) {
        CustomerRepository repository = new InMemoryCustomerRepository();
        Fixture fixture = criarConversationServiceCompleto(new CustomerProfileService(repository));
        fixture.customerProfileService.salvarNome(numero, nome);
        return new Fixture(
                numero,
                fixture.conversationService,
                fixture.customerProfileService,
                fixture.appointmentApplicationService
        );
    }

    private Fixture criarFixtureSemNome(String numero) {
        CustomerRepository repository = new InMemoryCustomerRepository();
        return new Fixture(
                numero,
                criarConversationServiceCompleto(new CustomerProfileService(repository)).conversationService(),
                new CustomerProfileService(repository),
                criarConversationServiceCompleto(new CustomerProfileService(new InMemoryCustomerRepository())).appointmentApplicationService()
        );
    }

    private record Fixture(String numero,
                           ConversationService conversationService,
                           CustomerProfileService customerProfileService,
                           AppointmentApplicationService appointmentApplicationService) {
    }

    private static class StubOllamaService extends OllamaService {
        StubOllamaService() {
            super(new AiConfiguration());
        }

        @Override
        public String responder(String mensagem) {
            return "Resposta de fallback.";
        }
    }
}
