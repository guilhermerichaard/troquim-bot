package com.troquim_bot.schedule;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentBookingServiceTest {

    private ScheduleService scheduleService;
    private AppointmentService appointmentService;
    private AppointmentBookingService bookingService;
    private InMemoryReservationRepository reservationRepository;
    private InMemoryAppointmentRepository appointmentRepository;
    private ReservationApplicationService reservationApplicationService;
    private AppointmentApplicationService appointmentApplicationService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService();
        appointmentService = new AppointmentService();
        reservationRepository = new InMemoryReservationRepository();
        appointmentRepository = new InMemoryAppointmentRepository();
        reservationApplicationService = new ReservationApplicationService(reservationRepository);
        appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        bookingService = new AppointmentBookingService(
                scheduleService,
                appointmentService,
                reservationApplicationService,
                appointmentApplicationService
        );
    }

    // Cenário 1: Quando o slot está livre
    @Test
    void deveReservarSlotECriarAppointmentQuandoSlotLivre() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "10:00"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 10:00 para você.", resultado);
        
        // Verifica que o slot foi reservado
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));
        
        // Verifica que o Appointment foi criado via Reservation (apenas 1)
        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(1, appointments.size());
        
        // Verifica que a Reservation foi cancelada (consumida)
        List<Reservation> reservations = reservationApplicationService.listarTodos();
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CANCELADO, reservations.get(0).getStatus());
    }

    // Cenário 2: Quando o slot não está livre
    @Test
    void naoDeveCriarAppointmentQuandoSlotOcupado() {
        // Reserva o slot primeiro
        scheduleService.reservarHorario("segunda", "10:00", "5511888888888");

        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "10:00"
        );

        assertTrue(resultado.contains("não está mais disponível"));
        
        // Verifica que NÃO criou Appointment
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(0, appointments.size());
    }

    // Cenário 3: Normalização de horário
    @Test
    void deveCriarReservationEAppointmentDeReservaQuandoSlotLivre() {
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        bookingService = new AppointmentBookingService(
                scheduleService,
                appointmentService,
                reservationApplicationService,
                appointmentApplicationService
        );

        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "10:00"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 10:00 para você.", resultado);

        List<Reservation> reservations = reservationApplicationService.listarTodos();
        assertEquals(1, reservations.size());
        assertEquals(ReservationStatus.CANCELADO, reservations.get(0).getStatus());

        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(1, appointments.size());
        assertEquals(reservations.get(0).getId(), appointments.get(0).getReservationId());
    }

    @Test
    void naoDeveCriarReservationOuAppointmentQuandoSlotOcupado() {
        InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
        InMemoryAppointmentRepository appointmentRepository = new InMemoryAppointmentRepository();
        ReservationApplicationService reservationApplicationService = new ReservationApplicationService(reservationRepository);
        AppointmentApplicationService appointmentApplicationService = new AppointmentApplicationService(
                appointmentRepository,
                reservationRepository
        );
        bookingService = new AppointmentBookingService(
                scheduleService,
                appointmentService,
                reservationApplicationService,
                appointmentApplicationService
        );
        scheduleService.reservarHorario("segunda", "10:00", "5511888888888");

        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "10:00"
        );

        assertTrue(resultado.contains("não está mais disponível"));
        assertTrue(reservationApplicationService.listarTodos().isEmpty());
        assertTrue(appointmentApplicationService.listarTodos().isEmpty());
        assertTrue(appointmentService.listarAgendamentosDoCliente("5511999999999").isEmpty());
    }

    @Test
    void deveNormalizarHorarioComH() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "15h"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 15h para você.", resultado);
        
        // Verifica que normalizou corretamente
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "15:00"));
    }

    @Test
    void deveNormalizarHorarioComHMinuto() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "9h"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 9h para você.", resultado);
        
        // Verifica que normalizou corretamente (9h → 09:00)
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "09:00"));
    }

    @Test
    void deveNormalizarHorarioComHs() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "14hs"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 14hs para você.", resultado);
        
        // Verifica que normalizou corretamente (14hs → 14:00)
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "14:00"));
    }

    @Test
    void deveRetornarIndisponivelParaHorarioInexistente() {
        // Horário 14:30 não existe na agenda (apenas slots de hora em hora)
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "14:30"
        );

        assertTrue(resultado.contains("não está mais disponível"));
        
        // Verifica que NÃO criou Appointment
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(0, appointments.size());
    }

    // Cenário 4: Entrada inválida
    @Test
    void deveRetornarErroQuandoDiaVazio() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "",
                "10:00"
        );

        // Dia vazio não existe na agenda, então retorna indisponível
        assertTrue(resultado.contains("não está mais disponível"));
    }

    @Test
    void deveRetornarErroQuandoHorarioVazio() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                ""
        );

        // Horário vazio não existe, então retorna indisponível
        assertTrue(resultado.contains("não está mais disponível"));
    }

    @Test
    void deveCriarAppointmentMesmoComServicoVazio() {
        // Serviço vazio não impede a criação, apenas cria com nome vazio
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "",
                "segunda",
                "10:00"
        );

        assertEquals("Perfeito! Vou reservar  na segunda às 10:00 para você.", resultado);
        
        // Verifica que o Appointment foi criado via Reservation (apenas 1)
        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(1, appointments.size());
    }

    @Test
    void deveCriarAppointmentMesmoComNomeVazio() {
        // Nome vazio não impede a criação
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "",
                "unha",
                "segunda",
                "10:00"
        );

        assertEquals("Perfeito! Vou reservar unha na segunda às 10:00 para você.", resultado);
        
        // Verifica que o Appointment foi criado via Reservation (apenas 1)
        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(1, appointments.size());
    }

    @Test
    void naoDeveReservarDomingo() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "domingo",
                "10:00"
        );

        assertTrue(resultado.contains("não está mais disponível"));
        
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(0, appointments.size());
    }

    @Test
    void naoDeveReservarHorarioInexistente() {
        String resultado = bookingService.bookIfAvailable(
                "5511999999999",
                "Guilherme",
                "unha",
                "segunda",
                "25:00"
        );

        assertTrue(resultado.contains("não está mais disponível"));
        
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(0, appointments.size());
    }

    // Teste de isAvailable
    @Test
    void deveVerificarDisponibilidadeCorretamente() {
        assertTrue(bookingService.isAvailable("segunda", "10:00"));
        
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "unha", "segunda", "10:00");
        
        assertFalse(bookingService.isAvailable("segunda", "10:00"));
        assertTrue(bookingService.isAvailable("segunda", "11:00"));
    }

    // Teste de múltiplas reservas
    @Test
    void devePermitirMultiplasReservasEmDiasDiferentes() {
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "unha", "segunda", "10:00");
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "cabelo", "terça", "11:00");

        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(2, appointments.size());
    }

    @Test
    void devePermitirMultiplasReservasEmHorariosDiferentes() {
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "unha", "segunda", "10:00");
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "cabelo", "segunda", "11:00");

        List<com.troquim_bot.appointment.Appointment> appointments = appointmentApplicationService.listarTodos();
        assertEquals(2, appointments.size());
    }
}
