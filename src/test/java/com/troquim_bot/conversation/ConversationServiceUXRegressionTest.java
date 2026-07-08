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
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationServiceUXRegressionTest {

    @Test
    void agendarUnhaNaoReutilizaDiaHorarioAntigo() {
        // Cenário 1: "agendar unha" não pode reutilizar dia/horário antigo
        Fixture fixture = criarFixtureComNome("5511000000001");

        // Primeiro agendamento completo
        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());

        // Novo agendamento: "agendar unha" deve perguntar o dia, não reutilizar
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "agendar unha");

        assertTrue(resposta.toLowerCase().contains("dia") || resposta.toLowerCase().contains("qual"),
            "Deve perguntar o dia, não reutilizar o antigo. Resposta: " + resposta);
    }

    @Test
    void unhaSegundaAs13ReservaDiretamente() {
        // Cenário 2: "unha segunda as 13" deve reservar diretamente
        Fixture fixture = criarFixtureComNome("5511000000002");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "unha segunda as 13");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size(),
            "Deve criar um agendamento");
        assertTrue(resposta.contains("reservado") || resposta.contains("Perfeito"),
            "Deve confirmar a reserva. Resposta: " + resposta);
    }

    @Test
    void resposta11UsaComoHorario() {
        // Cenário 3: Após listar horários, "11" deve ser usado como 11h
        Fixture fixture = criarFixtureComNome("5511000000003");

        // Inicia fluxo de agendamento
        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");

        // Responde "11" como horário
        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "11");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size(),
            "Deve criar um agendamento com 11h");
        assertTrue(resposta.contains("reservado") || resposta.contains("Perfeito"),
            "Deve confirmar a reserva. Resposta: " + resposta);
    }

    @Test
    void perguntaForaDeEscopoRedireciona() {
        // Cenário 4: Pergunta fora de escopo deve redirecionar educadamente
        Fixture fixture = criarFixtureSemNome("5511000000004");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "Corinthians ou Flamengo?");

        assertTrue(resposta.contains("agendamento") || resposta.contains("serviço") || resposta.contains("marcar"),
            "Deve redirecionar para agendamento. Resposta: " + resposta);
    }

    @Test
    void cancelarAgendamentoContinuaFuncionando() {
        // Cenário 5: Cancelamento deve continuar funcionando
        Fixture fixture = criarFixtureComNome("5511000000005");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size());

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "cancelar agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
        assertEquals(0, fixture.appointmentApplicationService.listarAtivos().size());
    }

    @Test
    void apagarAgendamentoContinuaFuncionando() {
        Fixture fixture = criarFixtureComNome("5511000000006");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "apagar agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
    }

    @Test
    void removerAgendamentoContinuaFuncionando() {
        Fixture fixture = criarFixtureComNome("5511000000007");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "remover agendamento");

        assertEquals("Seu agendamento foi cancelado com sucesso.", resposta);
    }

    @Test
    void consultarAgendamentoContinuaFuncionando() {
        Fixture fixture = criarFixtureComNome("5511000000008");

        fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        fixture.conversationService.gerarResposta(fixture.numero, "13h");

        String resposta = fixture.conversationService.gerarResposta(fixture.numero, "qual meu agendamento");

        assertTrue(resposta.contains("unha"), "Deve mostrar o serviço agendado");
        assertTrue(resposta.contains("segunda"), "Deve mostrar o dia");
        assertTrue(resposta.contains("13h") || resposta.contains("13"), "Deve mostrar o horário");
    }

    @Test
    void fluxoCompletoServicoDiaHorario() {
        // Fluxo principal: escolher serviço -> dia -> horário -> confirmar
        Fixture fixture = criarFixtureComNome("5511000000009");

        String r1 = fixture.conversationService.gerarResposta(fixture.numero, "quero agendar unha");
        assertTrue(r1.toLowerCase().contains("dia") || r1.toLowerCase().contains("qual"),
            "Passo 1: deve perguntar o dia. Resposta: " + r1);

        String r2 = fixture.conversationService.gerarResposta(fixture.numero, "segunda");
        assertTrue(r2.toLowerCase().contains("horario") || r2.toLowerCase().contains("qual") || r2.toLowerCase().contains("tenho"),
            "Passo 2: deve perguntar o horário ou listar disponíveis. Resposta: " + r2);

        String r3 = fixture.conversationService.gerarResposta(fixture.numero, "13h");
        assertTrue(r3.contains("reservado") || r3.contains("Perfeito"),
            "Passo 3: deve confirmar a reserva. Resposta: " + r3);

        assertEquals(1, fixture.appointmentApplicationService.listarTodos().size(),
            "Deve criar exatamente 1 agendamento");
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