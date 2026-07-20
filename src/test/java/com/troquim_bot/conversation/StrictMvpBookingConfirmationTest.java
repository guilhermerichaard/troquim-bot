package com.troquim_bot.conversation;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o Bug 2 do STRICT_MVP: ao confirmar, o fluxo deve realmente criar
 * Customer, Reservation e Appointment persistidos e finalizar o estado.
 */
class StrictMvpBookingConfirmationTest {

    private ReservationApplicationService reservationApp;
    private AppointmentApplicationService appointmentApp;
    private CustomerRepository customerRepository;
    private ConversationStateService conversationStateService;
    private StrictMvpMenuService menu;

    @BeforeEach
    void setUp() {
        ReservationRepository reservationRepository = new InMemoryReservationRepository();
        reservationApp = new ReservationApplicationService(reservationRepository);
        appointmentApp = new AppointmentApplicationService(
                new InMemoryAppointmentRepository(), reservationRepository);
        customerRepository = new InMemoryCustomerRepository();
        CustomerProfileService customerProfileService = new CustomerProfileService(customerRepository, TestTenants.pilot());

        BookingApplicationService booking = new BookingApplicationService(
                reservationApp, appointmentApp, customerProfileService);

        conversationStateService = new ConversationStateService(new InMemoryConversationStateRepository());
        menu = new StrictMvpMenuService(
                conversationStateService,
                new AvailabilityApplicationService(),
                booking,
                "STRICT_MVP");
    }

    /** Envia uma mensagem como o ConversationOrchestrator faz: buscando o estado do repositório. */
    private String enviar(String numero, String mensagem) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        return menu.processarMenu(numero, mensagem, state);
    }

    /** Percorre o fluxo até a tela de confirmação (sem confirmar ainda). */
    private void percorrerAteConfirmacao(String numero) {
        enviar(numero, "oi");      // menu principal
        enviar(numero, "1");       // agendar
        enviar(numero, "cabelo");  // serviço
        enviar(numero, "sexta");   // dia
        enviar(numero, "5");       // horário índice 5 = 13:00
        enviar(numero, "Maria");   // nome
    }

    @Test
    void deveCriarCustomerReservationEAppointmentAoConfirmar() {
        String numero = "5511900000001";
        percorrerAteConfirmacao(numero);

        String confirmacao = enviar(numero, "1");

        assertTrue(confirmacao.contains("registrado com sucesso"),
                "Esperava mensagem de sucesso, mas veio: " + confirmacao);

        // Reservation criada e cancelada (o Appointment protege o slot, não a Reservation)
        assertEquals(1, reservationApp.listarTodos().size(), "Deveria haver 1 reserva");
        assertEquals(0, reservationApp.listarAtivos().size(), "A reserva deve estar cancelada após criar o Appointment");

        // Appointment criado
        assertEquals(1, appointmentApp.listarTodos().size(), "Deveria haver 1 agendamento");

        // Customer criado, identificado pela chave lógica (tenant + telefone)
        assertTrue(customerRepository.findByBusinessAndPhone(TestTenants.PILOT, new PhoneNumber(numero)).isPresent(),
                "O cliente deveria ter sido criado");
        assertEquals(1, customerRepository.findByBusinessId(TestTenants.PILOT).size());

        // Estado finalizado
        ConversationState estadoFinal = conversationStateService.buscarPorNumero(numero);
        assertEquals(ConversationStep.FINALIZADO, estadoFinal.getStep(), "O estado deveria estar FINALIZADO");
        assertTrue(estadoFinal.getDraftAtual().isConfirmado(), "O draft deveria estar marcado como confirmado");
    }

    @Test
    void confirmarDuplicadoNaoCriaAgendamentoDuplicado() {
        String numero = "5511900000002";
        percorrerAteConfirmacao(numero);
        enviar(numero, "1"); // primeira confirmação — cria os dados

        assertEquals(1, reservationApp.listarTodos().size());
        assertEquals(1, appointmentApp.listarTodos().size());

        // Simula uma nova entrega da confirmação enquanto o draft já está confirmado.
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        state.setStep(ConversationStep.AGUARDANDO_CONFIRMACAO);
        conversationStateService.persistir(state);

        String segunda = enviar(numero, "1");

        assertTrue(segunda.contains("ja esta registrado"),
                "Esperava mensagem de idempotência, mas veio: " + segunda);
        assertEquals(1, reservationApp.listarTodos().size(), "Não pode duplicar a reserva");
        assertEquals(1, appointmentApp.listarTodos().size(), "Não pode duplicar o agendamento");
    }

    @Test
    void horarioOcupadoNaoCriaDadosParciaisEMantemEstadoEmConfirmacao() {
        String primeiro = "5511900000003";
        percorrerAteConfirmacao(primeiro);
        enviar(primeiro, "1"); // ocupa sexta 13:00

        assertEquals(1, reservationApp.listarTodos().size());
        assertEquals(1, appointmentApp.listarTodos().size());

        // Segundo cliente tenta o mesmo horário.
        String segundo = "5511900000004";
        percorrerAteConfirmacao(segundo);
        String resposta = enviar(segundo, "1");

        assertTrue(resposta.toLowerCase().contains("ocupado"),
                "Esperava aviso de horário ocupado, mas veio: " + resposta);

        // Nenhum dado parcial para o segundo cliente
        assertEquals(0, reservationApp.listarAtivos().size(), "Nenhuma reserva deve estar ativa (a do primeiro foi cancelada após criar Appointment)");
        assertEquals(1, appointmentApp.listarTodos().size(), "Não pode criar agendamento para o horário ocupado");
        assertEquals(1, customerRepository.findByBusinessId(TestTenants.PILOT).size(), "Cliente do horário ocupado não deve ser persistido");

        // Estado do segundo cliente permanece em confirmação (não finaliza)
        ConversationState estadoSegundo = conversationStateService.buscarPorNumero(segundo);
        assertEquals(ConversationStep.AGUARDANDO_CONFIRMACAO, estadoSegundo.getStep());
    }
}
