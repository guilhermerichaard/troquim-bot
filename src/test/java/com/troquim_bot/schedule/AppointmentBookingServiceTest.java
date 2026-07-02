package com.troquim_bot.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentBookingServiceTest {

    private ScheduleService scheduleService;
    private AppointmentService appointmentService;
    private AppointmentBookingService bookingService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService();
        appointmentService = new AppointmentService();
        bookingService = new AppointmentBookingService(scheduleService, appointmentService);
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
        
        // Verifica que o Appointment foi criado
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(1, appointments.size());
        assertEquals("unha", appointments.get(0).getServico());
        assertEquals("segunda", appointments.get(0).getDia());
        assertEquals("10:00", appointments.get(0).getHorario());
        assertEquals(AppointmentStatus.PENDENTE, appointments.get(0).getStatus());
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
        
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
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
        
        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(1, appointments.size());
        assertEquals("", appointments.get(0).getNomeCliente());
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

        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(2, appointments.size());
    }

    @Test
    void devePermitirMultiplasReservasEmHorariosDiferentes() {
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "unha", "segunda", "10:00");
        bookingService.bookIfAvailable("5511999999999", "Guilherme", "cabelo", "segunda", "11:00");

        List<Appointment> appointments = appointmentService.listarAgendamentosDoCliente("5511999999999");
        assertEquals(2, appointments.size());
    }
}