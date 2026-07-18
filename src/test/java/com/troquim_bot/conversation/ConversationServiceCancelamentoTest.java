package com.troquim_bot.conversation;

import com.troquim_bot.ai.config.AiConfiguration;
import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationServiceCancelamentoTest {

    @Test
    void cancelarAgendamentoSemAgendamentoAtivo() {
        Fixture fixture = criarFixtureSemNome("5511000000001");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Quero cancelar meu agendamento");

        assertEquals("Você não tem nenhum agendamento ativo para cancelar.", resposta);
    }

    @Test
    void cancelarAgendamentoComUmAgendamentoCancelaAutomaticamente() {
        Fixture fixture = criarFixtureComNome("5511000000002");

        // Cria um agendamento
        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());

        // Cancela
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Quero cancelar meu agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
        assertEquals(0, fixture.appointmentApplicationService.listarAtivos().size());
    }

    @Test
    void cancelarAgendamentoComMultiplosAgendamentosPerguntaQual() {
        Fixture fixture = criarFixtureComNome("5511000000003");

        // Cria primeiro agendamento
        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        // Cria segundo agendamento
        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar cabelo");
        fixture.conversationService.gerarResposta(fixture.numero, "terça");
        fixture.conversationService.gerarResposta(fixture.numero, "14h");

        assertEquals(2, fixture.appointmentApplicationService.listarTodos().size());

        // Tenta cancelar
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Quero cancelar meu agendamento");

        assertTrue(resposta.contains("mais de um agendamento"), "Deve perguntar qual agendamento cancelar");
        assertTrue(resposta.contains("1."), "Deve listar o primeiro agendamento");
        assertTrue(resposta.contains("2."), "Deve listar o segundo agendamento");
        assertEquals(2, fixture.appointmentApplicationService.listarAtivos().size(),
            "Nenhum agendamento deve ser cancelado automaticamente");
    }

    @Test
    void cancelarPalavraChaveFunciona() {
        Fixture fixture = criarFixtureComNome("5511000000004");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "cancelar");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
    }

    @Test
    void excluirAgendamentoFunciona() {
        Fixture fixture = criarFixtureComNome("5511000000005");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "excluir agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
    }

    @Test
    void apagarAgendamentoFunciona() {
        Fixture fixture = criarFixtureComNome("5511000000006");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "apagar agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
    }

    @Test
    void cancelarNaoIniciaFluxoDeBooking() {
        Fixture fixture = criarFixtureSemNome("5511000000007");

        // "cancelar" não deve iniciar fluxo de agendamento
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "cancelar");

        assertEquals("Você não tem nenhum agendamento ativo para cancelar.", resposta);
        assertEquals(0, fixture.appointmentApplicationService.listarTodos().size());
    }

    private Fixture criarFixtureComNome(String numero) {
        Fixture fixture = criarFixtureSemNome(numero);
        fixture.customerProfileService.salvarNome(numero, "Guilherme");
        return fixture;
    }

    private Fixture criarFixtureSemNome(String numero) {
        CustomerProfileService customerProfileService = new CustomerProfileService(new InMemoryCustomerRepository());
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService =
                new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        AvailabilityApplicationService availabilityApplicationService = new AvailabilityApplicationService(
                new com.troquim_bot.repository.InMemoryAvailabilityRepository(),
                scheduleService
        );
        ConversationService conversationService = new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                new ConversationStateService(new InMemoryConversationStateRepository()),
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
                availabilityApplicationService,
                new StrictMvpMenuService(
                        new ConversationStateService(new InMemoryConversationStateRepository()),
                        availabilityApplicationService,
                        new BookingApplicationService(
                                new ReservationApplicationService(new InMemoryReservationRepository()),
                                new AppointmentApplicationService(),
                                new CustomerProfileService(new InMemoryCustomerRepository())),
                        "NORMAL"
                )
        );

        return new Fixture(
                numero,
                conversationService,
                customerProfileService,
                appointmentApplicationService
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