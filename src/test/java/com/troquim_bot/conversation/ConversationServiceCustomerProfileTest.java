package com.troquim_bot.conversation;

import com.troquim_bot.ai.config.AiConfiguration;
import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConversationServiceCustomerProfileTest {

    @Test
    void salvaNomeEUsaPerfilEmAtendimentosFuturos() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        ConversationService conversationService = criarConversationServiceCompleto(
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
        ConversationService conversationService = criarConversationServiceCompleto(
                customerProfileService
        );

        String numero = "5511888888888";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "Oi");
        conversationService.gerarResposta(numero, "quero manicure");
        conversationService.gerarResposta(numero, "sexta");
        String resposta = conversationService.gerarResposta(numero, "16h");

        assertEquals("Perfeito, Guilherme. Seu horário para manicure na sexta às 16h foi reservado.", resposta);
    }

    @Test
    void fluxoDeConversaCriaAppointmentAPartirDeReservation() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                appointmentService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService
                )
        );

        String numero = "5511222222222";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        String resposta = conversationService.gerarResposta(numero, "10h");

        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 10h foi reservado.", resposta);

        assertEquals(1, reservationApplicationService.listarTodos().size());
        Reservation reservation = reservationApplicationService.listarTodos().get(0);
        assertEquals(ReservationStatus.CANCELADO, reservation.getStatus());

        assertEquals(1, appointmentApplicationService.listarTodos().size());
        com.troquim_bot.appointment.Appointment appointment = appointmentApplicationService.listarTodos().get(0);
        assertNotNull(appointment.getReservationId());
        assertEquals(reservation.getId(), appointment.getReservationId());
    }

    @Test
    void respondeIndisponivelENaoCriaAppointmentQuandoHorarioOcupado() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                appointmentService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService
                )
        );

        String numero = "5511777777777";
        customerProfileService.salvarNome(numero, "Guilherme");
        scheduleService.reservarHorario("segunda", "10:00", "5511000000000");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        String resposta = conversationService.gerarResposta(numero, "10h");

        assertEquals("Esse horário não está disponível. Quer tentar outro horário?", resposta);
        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());
        assertEquals(0, appointmentApplicationService.listarTodos().size());
    }

    @Test
    void continuaPerguntandoDadoFaltanteAntesDeReservar() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ConversationService conversationService = criarConversationServiceCompleto(
                customerProfileService
        );

        String numero = "5511444444444";

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        String respostaAntesDoNome = conversationService.gerarResposta(numero, "15h");

        assertEquals("Perfeito. Como você prefere que eu te chame?", respostaAntesDoNome);
        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());

        String respostaComNome = conversationService.gerarResposta(numero, "Meu nome é Ana");

        assertEquals("Perfeito, Ana. Seu horário para unha na segunda às 15h foi reservado.", respostaComNome);
        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());
    }

    @Test
    void estadoPendenteNaoMonopolizaNovasIntencoes() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        ConversationService conversationService = criarConversationServiceCompleto(customerProfileService);

        String numero = "5511666666666";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        conversationService.gerarResposta(numero, "15h");

        assertEquals("Boa tarde, Guilherme! Como posso ajudar?", conversationService.gerarResposta(numero, "Oi"));
        assertEquals("Seu nome está salvo como Guilherme.", conversationService.gerarResposta(numero, "Qual meu nome?"));
        assertEquals("Lembro sim, Guilherme. Como posso ajudar?", conversationService.gerarResposta(numero, "Lembra de mim?"));
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.",
                conversationService.gerarResposta(numero, "Agendou mesmo?"));
    }

    @Test
    void criaAppointmentPendenteQuandoFluxoFicaCompleto() {
        CustomerProfileService customerProfileService = new CustomerProfileService();
        AppointmentService appointmentService = new AppointmentService();
        ConversationService conversationService = criarConversationServiceCompleto(customerProfileService);

        String numero = "5511555555555";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "terça");
        conversationService.gerarResposta(numero, "15h");

        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.",
                conversationService.gerarResposta(numero, "qual meu agendamento?"));
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.",
                conversationService.gerarResposta(numero, "qual horário?"));
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.",
                conversationService.gerarResposta(numero, "qual serviço?"));
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.",
                conversationService.gerarResposta(numero, "marquei para quando?"));
    }

    private ConversationService criarConversationServiceCompleto(CustomerProfileService customerProfileService) {
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        return criarConversationService(
                customerProfileService,
                appointmentService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService
                )
        );
    }

    private ConversationService criarConversationService(CustomerProfileService customerProfileService,
                                                        AppointmentService appointmentService,
                                                        AppointmentBookingService appointmentBookingService) {
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
                appointmentBookingService
        );
    }
}
