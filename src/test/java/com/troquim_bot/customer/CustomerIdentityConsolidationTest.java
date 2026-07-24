package com.troquim_bot.customer;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.conversation.query.BookingQueryResponder;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a consolidação de identidade (ARCHITECTURE_V2_1 §C7/§C8): o CustomerId
 * OFICIAL é o surrogate persistido do agregado Customer, resolvido por
 * (BusinessId, phone E.164). Appointment, Reservation e Conversation usam esse
 * mesmo id — nunca {@code CustomerId.fromPhone}.
 */
class CustomerIdentityConsolidationTest {

    private static final PhoneNumber PHONE = new PhoneNumber("5511900000001");

    private InMemoryCustomerRepository customerRepository;
    private InMemoryReservationRepository reservationRepository;
    private InMemoryAppointmentRepository appointmentRepository;

    private CustomerProfileService profiles() {
        return new CustomerProfileService(customerRepository, TestTenants.pilot());
    }

    private BookingApplicationService booking(CustomerProfileService profiles) {
        return new BookingApplicationService(
                new ReservationApplicationService(reservationRepository),
                new AppointmentApplicationService(appointmentRepository, reservationRepository),
                profiles,
                new InMemoryBookingIdempotencyStore());
    }

    private void novoContexto() {
        customerRepository = new InMemoryCustomerRepository();
        reservationRepository = new InMemoryReservationRepository();
        appointmentRepository = new InMemoryAppointmentRepository();
    }

    // 1. Mesmo Business + telefone → mesmo CustomerId (o telefone é só chave de busca).
    @Test
    void mesmoBusinessETelefoneMantemMesmoCustomerId() {
        novoContexto();
        CustomerProfileService profiles = profiles();

        CustomerId primeiro = profiles.resolverIdOficial("5511900000001");
        // Mesma identidade lógica, formato diferente (a normalização E.164 é a chave).
        CustomerId segundo = profiles.resolverIdOficial("55 11 90000-0001");

        assertEquals(primeiro, segundo, "Mesmo (BusinessId, phone E.164) deve resolver o mesmo CustomerId");
        assertEquals(1, customerRepository.findByBusinessId(TestTenants.PILOT).size(),
                "Não pode duplicar Customer para o mesmo telefone");
    }

    // 2. Businesses diferentes → CustomerIds diferentes (corrige a colisão cross-tenant do fromPhone).
    @Test
    void businessesDiferentesGeramCustomerIdsDiferentes() {
        novoContexto();
        CustomerProfileService pilot = new CustomerProfileService(customerRepository, TestTenants.pilot());
        CustomerProfileService outro = new CustomerProfileService(customerRepository, TestTenants.of(TestTenants.OUTRO));

        CustomerId idPilot = pilot.resolverIdOficial("5511900000001");
        CustomerId idOutro = outro.resolverIdOficial("5511900000001");

        assertNotEquals(idPilot, idOutro, "Mesmo telefone em dois tenants deve gerar CustomerIds distintos");
        // E nenhum deles é o id derivado do telefone.
        assertNotEquals(CustomerId.fromPhone("5511900000001"), idPilot);
    }

    // 3 e 4. Appointment e Reservation recebem o CustomerId oficial persistido (fluxo STRICT_MVP).
    @Test
    void appointmentEReservationUsamOCustomerIdPersistido() {
        novoContexto();
        CustomerProfileService profiles = profiles();
        ReservationApplicationService reservationApp = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApp = new AppointmentApplicationService(appointmentRepository, reservationRepository);
        BookingApplicationService booking = new BookingApplicationService(reservationApp, appointmentApp, profiles,
                new InMemoryBookingIdempotencyStore());

        BookingResult resultado = booking.confirmar("5511900000001", "Maria Silva", "cabelo", "sexta", "13h");
        assertTrue(resultado.isConfirmado(), "O agendamento deveria ser confirmado");

        CustomerId oficial = customerRepository.findByBusinessAndPhone(TestTenants.PILOT, PHONE)
                .orElseThrow().getId();

        Appointment appointment = appointmentApp.listarTodos().get(0);
        Reservation reservation = reservationApp.listarTodos().get(0);

        assertEquals(oficial, appointment.getCustomerId(), "Appointment deve usar o CustomerId persistido");
        assertEquals(oficial, reservation.getCustomerId(), "Reservation deve usar o mesmo CustomerId oficial");
        assertNotEquals(CustomerId.fromPhone("5511900000001"), appointment.getCustomerId(),
                "Não pode usar o id derivado do telefone");
    }

    // 5. A Conversation NÃO deriva id por telefone: consulta pela identidade oficial e,
    //    sem Customer, não enxerga um Appointment gravado sob o id legado fromPhone.
    @Test
    void conversationNaoDerivaIdPorTelefone() {
        novoContexto();
        CustomerProfileService profiles = profiles();
        AppointmentApplicationService appointmentApp = new AppointmentApplicationService(appointmentRepository, reservationRepository);
        BookingQueryResponder responder = new BookingQueryResponder(
                appointmentApp, new AvailabilityApplicationService(), profiles);

        String numero = "5511900000001";
        LocalDate data = LocalDate.now().plusDays(1);
        // Agendamento gravado sob o id LEGADO derivado do telefone, sem Customer persistido.
        appointmentApp.criarAgendamento(
                CustomerId.fromPhone(numero),
                ProfessionalId.from(UUID.randomUUID()),
                ServiceId.from(UUID.randomUUID()),
                AvailabilityId.from(UUID.randomUUID()),
                data, LocalTime.of(10, 0), LocalTime.of(11, 0));

        Optional<String> resposta = responder.responderConsultaAgendamento(
                numero, IntentType.CONSULTAR_AGENDAMENTO, "meu agendamento", new ConversationState(numero));

        assertTrue(resposta.isPresent());
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.", resposta.get(),
                "A Conversation não pode encontrar o agendamento pelo id derivado do telefone");
        assertFalse(customerRepository.findByBusinessAndPhone(TestTenants.PILOT, new PhoneNumber(numero)).isPresent(),
                "A consulta não pode criar Customer");
    }

    // 6. Reinício preserva a relação: o surrogate persistido é estável entre instâncias.
    @Test
    void reinicioPreservaARelacao() {
        novoContexto();
        CustomerId antes = profiles().resolverIdOficial("5511900000001");

        // "Reinício": nova instância do serviço sobre o MESMO repositório persistido.
        CustomerId depois = profiles().localizarIdOficial("5511900000001").orElseThrow();

        assertEquals(antes, depois, "O CustomerId oficial deve sobreviver ao reinício");
    }
}
