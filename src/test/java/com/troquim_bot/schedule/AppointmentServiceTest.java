package com.troquim_bot.schedule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppointmentServiceTest {

    private final AppointmentService service = new AppointmentService();

    @Test
    void criaEListaAgendamentosDoCliente() {
        Appointment appointment = service.criarAgendamento(
                "5511999999999",
                "Guilherme",
                "unha",
                "terça",
                "15h"
        );

        List<Appointment> appointments = service.listarAgendamentosDoCliente("5511999999999");

        assertNotNull(appointment.getId());
        assertEquals(1, appointments.size());
        assertEquals(appointment.getId(), appointments.get(0).getId());
        assertEquals(AppointmentStatus.PENDENTE, appointment.getStatus());
        assertEquals(appointment.getCriadoEm(), appointment.getAtualizadoEm());
    }

    @Test
    void buscaUltimoAgendamentoPorTelefone() {
        service.criarAgendamento("5511888888888", "Guilherme", "unha", "segunda", "10h");
        Appointment ultimo = service.criarAgendamento("5511888888888", "Guilherme", "cabelo", "sexta", "16h");

        assertEquals(ultimo.getId(),
                service.buscarUltimoAgendamentoPorTelefone("5511888888888").orElseThrow().getId());
    }

    @Test
    void atualizaStatusECancelaAgendamento() {
        Appointment appointment = service.criarAgendamento(
                "5511777777777",
                "Guilherme",
                "unha",
                "quinta",
                "14h"
        );

        service.atualizarStatus(appointment.getId(), AppointmentStatus.CONFIRMADO);
        assertEquals(AppointmentStatus.CONFIRMADO, appointment.getStatus());

        service.cancelarAgendamento(appointment.getId());
        assertEquals(AppointmentStatus.CANCELADO, appointment.getStatus());
    }
}
