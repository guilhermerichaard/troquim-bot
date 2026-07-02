package com.troquim_bot.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleServiceTest {

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService();
    }

    @Test
    void agendaPadraoDeveTerDiasUteis() {
        List<ScheduleSlot> segunda = scheduleService.listarHorarios("segunda");
        List<ScheduleSlot> terca = scheduleService.listarHorarios("terça");
        List<ScheduleSlot> quarta = scheduleService.listarHorarios("quarta");
        List<ScheduleSlot> quinta = scheduleService.listarHorarios("quinta");
        List<ScheduleSlot> sexta = scheduleService.listarHorarios("sexta");
        List<ScheduleSlot> sabado = scheduleService.listarHorarios("sábado");
        List<ScheduleSlot> domingo = scheduleService.listarHorarios("domingo");

        assertFalse(segunda.isEmpty());
        assertFalse(terca.isEmpty());
        assertFalse(quarta.isEmpty());
        assertFalse(quinta.isEmpty());
        assertFalse(sexta.isEmpty());
        assertFalse(sabado.isEmpty());
        assertTrue(domingo.isEmpty());
    }

    @Test
    void agendaPadraoDeveTerSlotsDe9hAs18h() {
        List<ScheduleSlot> slots = scheduleService.listarHorarios("segunda");

        assertEquals(9, slots.size());
        assertEquals("09:00", slots.get(0).getHorario());
        assertEquals("10:00", slots.get(1).getHorario());
        assertEquals("11:00", slots.get(2).getHorario());
        assertEquals("12:00", slots.get(3).getHorario());
        assertEquals("13:00", slots.get(4).getHorario());
        assertEquals("14:00", slots.get(5).getHorario());
        assertEquals("15:00", slots.get(6).getHorario());
        assertEquals("16:00", slots.get(7).getHorario());
        assertEquals("17:00", slots.get(8).getHorario());
    }

    @Test
    void todosSlotsDevemEstarLivresInicialmente() {
        List<ScheduleSlot> slots = scheduleService.listarHorarios("segunda");

        for (ScheduleSlot slot : slots) {
            assertEquals(SlotStatus.LIVRE, slot.getStatus());
            assertNull(slot.getNumeroCliente());
        }
    }

    @Test
    void deveListarHorariosDisponiveis() {
        scheduleService.reservarHorario("segunda", "10:00", "5511999999999");

        List<ScheduleSlot> disponiveis = scheduleService.listarHorariosDisponiveis("segunda");

        assertEquals(8, disponiveis.size());
        assertTrue(disponiveis.stream().noneMatch(s -> s.getHorario().equals("10:00")));
    }

    @Test
    void deveVerificarDisponibilidadeDeHorario() {
        assertTrue(scheduleService.isHorarioDisponivel("segunda", "10:00"));
        assertTrue(scheduleService.isHorarioDisponivel("terça", "15:00"));

        scheduleService.reservarHorario("segunda", "10:00", "5511999999999");

        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));
        assertTrue(scheduleService.isHorarioDisponivel("segunda", "11:00"));
    }

    @Test
    void deveRetornarFalseParaDiaInexistente() {
        assertFalse(scheduleService.isHorarioDisponivel("domingo", "10:00"));
        assertFalse(scheduleService.isHorarioDisponivel("feriado", "10:00"));
    }

    @Test
    void deveReservarHorarioDisponivel() {
        boolean resultado = scheduleService.reservarHorario("segunda", "10:00", "5511999999999");

        assertTrue(resultado);
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));
    }

    @Test
    void naoDeveReservarHorarioJaReservado() {
        scheduleService.reservarHorario("segunda", "10:00", "5511999999999");
        boolean resultado = scheduleService.reservarHorario("segunda", "10:00", "5511888888888");

        assertFalse(resultado);
    }

    @Test
    void naoDeveReservarDomingo() {
        boolean resultado = scheduleService.reservarHorario("domingo", "10:00", "5511999999999");

        assertFalse(resultado);
    }

    @Test
    void naoDeveReservarHorarioInexistente() {
        boolean resultado = scheduleService.reservarHorario("segunda", "25:00", "5511999999999");

        assertFalse(resultado);
    }

    @Test
    void deveCancelarReserva() {
        scheduleService.reservarHorario("segunda", "10:00", "5511999999999");
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));

        boolean resultado = scheduleService.cancelarReserva("segunda", "10:00");

        assertTrue(resultado);
        assertTrue(scheduleService.isHorarioDisponivel("segunda", "10:00"));
    }

    @Test
    void naoDeveCancelarHorarioNaoReservado() {
        boolean resultado = scheduleService.cancelarReserva("segunda", "10:00");

        assertFalse(resultado);
    }

    @Test
    void deveBloquearHorario() {
        boolean resultado = scheduleService.bloquearHorario("segunda", "10:00", "Manutenção");

        assertTrue(resultado);
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));
    }

    @Test
    void naoDeveBloquearHorarioJaReservado() {
        scheduleService.reservarHorario("segunda", "10:00", "5511999999999");
        boolean resultado = scheduleService.bloquearHorario("segunda", "10:00", "Manutenção");

        assertFalse(resultado);
    }

    @Test
    void naoDeveBloquearDomingo() {
        boolean resultado = scheduleService.bloquearHorario("domingo", "10:00", "Manutenção");

        assertFalse(resultado);
    }

    @Test
    void deveLiberarHorarioBloqueado() {
        scheduleService.bloquearHorario("segunda", "10:00", "Manutenção");
        assertFalse(scheduleService.isHorarioDisponivel("segunda", "10:00"));

        boolean resultado = scheduleService.liberarHorario("segunda", "10:00");

        assertTrue(resultado);
        assertTrue(scheduleService.isHorarioDisponivel("segunda", "10:00"));
    }

    @Test
    void naoDeveLiberarHorarioNaoBloqueado() {
        boolean resultado = scheduleService.liberarHorario("segunda", "10:00");

        assertFalse(resultado);
    }

    @Test
    void deveReservarHorarioEmQualquerDiaUtil() {
        assertTrue(scheduleService.reservarHorario("segunda", "10:00", "5511999999999"));
        assertTrue(scheduleService.reservarHorario("terça", "11:00", "5511999999999"));
        assertTrue(scheduleService.reservarHorario("quarta", "12:00", "5511999999999"));
        assertTrue(scheduleService.reservarHorario("quinta", "13:00", "5511999999999"));
        assertTrue(scheduleService.reservarHorario("sexta", "14:00", "5511999999999"));
        assertTrue(scheduleService.reservarHorario("sábado", "15:00", "5511999999999"));
    }
}