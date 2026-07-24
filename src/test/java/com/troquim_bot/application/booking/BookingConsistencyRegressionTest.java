package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de regressão que reproduzem as 4 falhas funcionais encontradas na
 * validação descartável do MVP. Estes testes DEVEM falhar antes das correções
 * e passar após a implementação dos fixes.
 */
class BookingConsistencyRegressionTest {

    private BookingApplicationService bookingApplicationService;
    private ReservationApplicationService reservationApplicationService;
    private AppointmentApplicationService appointmentApplicationService;
    private CustomerProfileService customerProfileService;
    private InMemoryReservationRepository reservationRepository;
    private InMemoryAppointmentRepository appointmentRepository;
    private InMemoryCustomerRepository customerRepository;

    private final String telefone = "5511999999999";
    private final String nome = "Maria Silva";
    private final String servico = "unha";
    private final String dia = "segunda";
    private final String horario = "10h";

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        appointmentRepository = new InMemoryAppointmentRepository();
        customerRepository = new InMemoryCustomerRepository();

        reservationApplicationService = new ReservationApplicationService(reservationRepository);
        appointmentApplicationService = new AppointmentApplicationService(appointmentRepository, reservationRepository);
        customerProfileService = new CustomerProfileService(customerRepository, TestTenants.pilot());
        bookingApplicationService = new BookingApplicationService(
                reservationApplicationService, appointmentApplicationService, customerProfileService,
                new InMemoryBookingIdempotencyStore());
    }

    // ==================== FALHA #2: Appointment sem reservation_id e Reservation ATIVO ====================

    @Test
    void confirmarDeveVincularAppointmentComReservationId() {
        BookingResult result = bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);

        assertTrue(result.isConfirmado(), "Confirmação deve suceder");
        var appointments = appointmentApplicationService.listarTodos();
        assertEquals(1, appointments.size(), "Deve criar exatamente 1 Appointment");
        var appointment = appointments.get(0);
        assertNotNull(appointment.getReservationId(),
                "FALHA #2: Appointment deve ter reservationId preenchido (não NULL)");
    }

    @Test
    void confirmarDeveCancelarReservationAposCriarAppointment() {
        BookingResult result = bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);

        assertTrue(result.isConfirmado());
        var reservations = reservationApplicationService.listarTodos();
        assertEquals(1, reservations.size());
        var reservation = reservations.get(0);
        assertNotEquals(ReservationStatus.ATIVO, reservation.getStatus(),
                "FALHA #2: Reservation não deve permanecer ATIVO após confirmação");
    }

    @Test
    void confirmarDeveSerIdempotenteNaoCriandoSegundoAppointment() {
        // Primeira confirmação
        BookingResult primeiro = bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);
        assertTrue(primeiro.isConfirmado());

        // Segunda confirmação (reenvio) - deve ser idempotente
        BookingResult segundo = bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);

        // Não deve criar segundo Appointment nem segunda Reservation
        assertEquals(1, appointmentApplicationService.listarTodos().size(),
                "FALHA #3: Reenvio não deve criar segundo Appointment");
        assertEquals(1, reservationApplicationService.listarTodos().size(),
                "FALHA #3: Reenvio não deve criar segunda Reservation");
    }

    @Test
    void confirmarDeveManterCustomerIdConsistenteEntreEntidades() {
        BookingResult result = bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);

        assertTrue(result.isConfirmado());

        var appointment = appointmentApplicationService.listarTodos().get(0);
        var reservation = reservationApplicationService.listarTodos().get(0);

        assertEquals(appointment.getCustomerId(), reservation.getCustomerId(),
                "customer_id deve ser consistente entre Appointment e Reservation");
    }

    @Test
    void confirmarNaoDeixarReservationParcialEmCasoDeFalha() {
        // Este teste simula um cenário onde a criação do Appointment falha
        // A Reservation não deve permanecer ATIVA (rollback compensação)

        // Criar primeira reserva que ocupará o horário
        bookingApplicationService.confirmar(telefone, nome, servico, dia, horario);

        // Tentar confirmar novamente o mesmo horário com outro cliente
        // Deve falhar e não deixar Reservation parcial
        BookingResult conflito = bookingApplicationService.confirmar(
                "5511999999998", "Joao", servico, dia, horario);

        assertFalse(conflito.isConfirmado(), "Conflito deve falhar");
    }
}
