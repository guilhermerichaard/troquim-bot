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
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConversationServiceCorrectionTest {

    @Test
    void agendaDepoisQualMeuAgendamentoRetornaAppointmentReal() {
        Fixture fixture = criarFixtureComNome("5511000000001");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());
        assertEquals("Você tem um agendamento para unha na segunda às 13h.",
                fixture.conversationService.gerarResposta(fixture.numero, "qual meu agendamento"));
    }

    @Test
    void agendeiRetornaAppointmentReal() {
        Fixture fixture = criarFixtureComNome("5511000000002");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals("Você tem um agendamento para unha na segunda às 13h.",
                fixture.conversationService.gerarResposta(fixture.numero, "agendei?"));
    }

    @Test
    void podeVerificarSeAgendeiServicoRetornaAppointmentReal() {
        Fixture fixture = criarFixtureComNome("5511000000003");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals("Você tem um agendamento para unha na segunda às 13h.",
                fixture.conversationService.gerarResposta(fixture.numero, "pode verificar se eu agendei unha?"));
    }

    @Test
    void queroAgendarUnhaDepoisDiaEHorarioJuntosCriaBookingReal() {
        Fixture fixture = criarFixtureComNome("5511000000004");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "segunda às 13");

        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 13h foi reservado.", resposta);
        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());
    }

    @Test
    void segundaUnhaAs13CriaBookingReal() {
        Fixture fixture = criarFixtureComNome("5511000000005");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "segunda unha às 13");

        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 13h foi reservado.", resposta);
        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());
    }

    @Test
    void unhaSegundaAs13CriaBookingReal() {
        Fixture fixture = criarFixtureComNome("5511000000006");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "unha segunda às 13");

        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 13h foi reservado.", resposta);
        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());
    }

    @Test
    void nomeJaSalvoNaoPerguntaNomeNovamente() {
        Fixture fixture = criarFixtureComNome("5511000000007");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "segunda às 13");

        assertFalse(resposta.contains("Como você prefere que eu te chame?"));
        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 13h foi reservado.", resposta);
    }

    @Test
    void dadosCompletosNaoRetornamTextoAntigoDeDisponibilidade() {
        Fixture fixture = criarFixtureComNome("5511000000008");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "agendar unha segunda às 13");

        assertFalse(resposta.toLowerCase().contains("vou verificar"));
        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 13h foi reservado.", resposta);
    }

    @Test
    void faqContinuaFuncionando() {
        Fixture fixture = criarFixtureSemNome("5511000000009");

        assertEquals("Estamos na Rua Augusta, 1500, Consolação, São Paulo.",
                fixture.conversationService.gerarResposta(fixture.numero, "qual o endereço?"));
    }

    @Test
    void fallbackContinuaFuncionando() {
        Fixture fixture = criarFixtureSemNome("5511000000010");

        assertEquals("Resposta de fallback.",
                fixture.conversationService.gerarResposta(fixture.numero, "minha cadeira é azul"));
    }

    @Test
    void quemSouEuRetornaNomeSalvo() {
        Fixture fixture = criarFixtureComNome("5511000000011");

        assertEquals("Seu nome está salvo como Guilherme.",
                fixture.conversationService.gerarResposta(fixture.numero, "Quem sou eu?"));
    }

    @Test
    void ataRetornaConfirmacaoCurta() {
        Fixture fixture = criarFixtureSemNome("5511000000012");

        assertEquals("Certo.", fixture.conversationService.gerarResposta(fixture.numero, "Ata"));
    }

    @Test
    void temHorarioParaServicoEDiaConsultaDisponibilidadeReal() {
        Fixture fixture = criarFixtureSemNome("5511000000013");

        assertEquals("Tenho horários para unha na segunda: 9h, 10h, 11h, 12h, 13h, 14h, 15h, 16h e 17h.",
                fixture.conversationService.gerarResposta(fixture.numero, "Tem horário para unha segunda?"));
    }

    @Test
    void agendarSabado17HorasBloqueiaForaDoExpediente() {
        Fixture fixture = criarFixtureComNome("5511000000014");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Agendar sábado 17 horas");

        assertEquals("Esse horário não está disponível. Qual outro horário você prefere?", resposta);
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
                        new BookingApplicationService(),
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
