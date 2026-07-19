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
import com.troquim_bot.conversation.state.AppointmentDraft;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.schedule.AppointmentService;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationServiceCustomerProfileTest {

    @Test
    void salvaNomeEUsaPerfilEmAtendimentosFuturos() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
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
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
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
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ConversationStateService conversationStateService = new ConversationStateService(new InMemoryConversationStateRepository());
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                appointmentService,
                conversationStateService,
                appointmentApplicationService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService,
                        customerProfileService
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
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ConversationStateService conversationStateService = new ConversationStateService(new InMemoryConversationStateRepository());
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        ConversationService conversationService = criarConversationService(
                customerProfileService,
                appointmentService,
                conversationStateService,
                appointmentApplicationService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService,
                        customerProfileService
                )
        );

        String numero = "5511777777777";
        customerProfileService.salvarNome(numero, "Guilherme");
        scheduleService.reservarHorario("segunda", "10:00", "5511000000000");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        String resposta = conversationService.gerarResposta(numero, "10h");

        assertEquals("Esse horário não está disponível. Qual outro horário você prefere?", resposta);
        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());
        assertEquals(0, reservationApplicationService.listarTodos().size());
        assertEquals(0, appointmentApplicationService.listarTodos().size());

        ConversationState state = conversationStateService.buscarPorNumero(numero);
        AppointmentDraft draft = state.getDraftAtual();
        assertNotNull(draft);
        assertEquals("unha", draft.getServico());
        assertEquals("segunda", draft.getDia());
        assertNull(draft.getHorario());
        assertEquals(ConversationStep.AGUARDANDO_HORARIO, state.getStep());
    }

    @Test
    void novoHorarioDepoisDeOcupadoCriaBookingSemReiniciarConversa() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
        AppointmentService appointmentService = new AppointmentService();
        ScheduleService scheduleService = new ScheduleService();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ConversationStateService css = new ConversationStateService(new InMemoryConversationStateRepository());
        ReservationApplicationService rsv = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService aas = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        ConversationService cs = criarConversationService(
                customerProfileService,
                appointmentService,
                css,
                aas,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        rsv,
                        aas,
                        customerProfileService
                )
        );

        String numero = "5511333333333";
        customerProfileService.salvarNome(numero, "Guilherme");
        scheduleService.reservarHorario("segunda", "10:00", "5511000000000");

        cs.gerarResposta(numero, "quero unha");
        cs.gerarResposta(numero, "segunda");
        assertEquals("Esse horário não está disponível. Qual outro horário você prefere?",
                cs.gerarResposta(numero, "10h"));

        String resposta = cs.gerarResposta(numero, "11h");

        assertEquals("Perfeito, Guilherme. Seu horário para unha na segunda às 11h foi reservado.", resposta);
        assertEquals(1, aas.listarTodos().size());
        assertEquals(1, rsv.listarTodos().size());
    }

    @Test
    void continuaPerguntandoDadoFaltanteAntesDeReservar() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
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
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
        ConversationService conversationService = criarConversationServiceCompleto(customerProfileService);

        String numero = "5511666666666";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "segunda");
        conversationService.gerarResposta(numero, "15h");

        assertEquals("Boa tarde, Guilherme! Como posso ajudar?", conversationService.gerarResposta(numero, "Oi"));
        assertEquals("Seu nome está salvo como Guilherme.", conversationService.gerarResposta(numero, "Qual meu nome?"));
        assertEquals("Lembro sim, Guilherme. Como posso ajudar?", conversationService.gerarResposta(numero, "Lembra de mim?"));
        assertEquals("Você tem um agendamento para unha na segunda às 15h.",
                conversationService.gerarResposta(numero, "Agendou mesmo?"));
    }

    @Test
    void criaAppointmentPendenteQuandoFluxoFicaCompleto() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(repository, TestTenants.pilot());
        AppointmentService appointmentService = new AppointmentService();
        ConversationService conversationService = criarConversationServiceCompleto(customerProfileService);

        String numero = "5511555555555";
        customerProfileService.salvarNome(numero, "Guilherme");

        conversationService.gerarResposta(numero, "quero unha");
        conversationService.gerarResposta(numero, "terça");
        conversationService.gerarResposta(numero, "15h");

        assertEquals(0, appointmentService.listarAgendamentosDoCliente(numero).size());
        assertEquals("Você tem um agendamento para unha na terça às 15h.",
                conversationService.gerarResposta(numero, "qual meu agendamento?"));
        assertEquals("Você tem um agendamento para unha na terça às 15h.",
                conversationService.gerarResposta(numero, "qual horário?"));
        assertEquals("Você tem um agendamento para unha na terça às 15h.",
                conversationService.gerarResposta(numero, "qual serviço?"));
        assertEquals("Você tem um agendamento para unha na terça às 15h.",
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
                new ConversationStateService(new InMemoryConversationStateRepository()),
                appointmentApplicationService,
                new AppointmentBookingService(
                        scheduleService,
                        appointmentService,
                        reservationApplicationService,
                        appointmentApplicationService,
                        customerProfileService
                )
        );
    }

    private ConversationService criarConversationService(CustomerProfileService customerProfileService,
                                                        AppointmentService appointmentService,
                                                        AppointmentBookingService appointmentBookingService) {
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );

        return criarConversationService(
                customerProfileService,
                appointmentService,
                new ConversationStateService(new InMemoryConversationStateRepository()),
                appointmentApplicationService,
                appointmentBookingService
        );
    }

    private ConversationService criarConversationService(CustomerProfileService customerProfileService,
                                                        AppointmentService appointmentService,
                                                        ConversationStateService conversationStateService,
                                                        AppointmentApplicationService appointmentApplicationService,
                                                        AppointmentBookingService appointmentBookingService) {
        ScheduleService scheduleService = new ScheduleService();
        AvailabilityApplicationService availabilityApplicationService = new AvailabilityApplicationService(
                new com.troquim_bot.repository.InMemoryAvailabilityRepository(),
                scheduleService
        );
        return new ConversationService(
                new IntentService(),
                new QuickResponseService(),
                new ContextService(),
                conversationStateService,
                new ConversationMemory(),
                new OllamaService(new AiConfiguration()),
                new PromptService(),
                customerProfileService,
                appointmentApplicationService,
                appointmentBookingService,
                availabilityApplicationService,
                new StrictMvpMenuService(
                        conversationStateService,
                        availabilityApplicationService,
                        new BookingApplicationService(
                                new ReservationApplicationService(new InMemoryReservationRepository()),
                                new AppointmentApplicationService(),
                                new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot())),
                        "NORMAL"
                )
        );
    }
}
