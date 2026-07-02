package com.troquim_bot.schedule;

import java.time.LocalDateTime;
import java.util.UUID;

public class Appointment {

    private final UUID id;
    private final String numeroCliente;
    private final String nomeCliente;
    private final String servico;
    private final String dia;
    private final String horario;
    private AppointmentStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public Appointment(UUID id,
                       String numeroCliente,
                       String nomeCliente,
                       String servico,
                       String dia,
                       String horario,
                       AppointmentStatus status,
                       LocalDateTime criadoEm,
                       LocalDateTime atualizadoEm) {
        this.id = id;
        this.numeroCliente = numeroCliente;
        this.nomeCliente = nomeCliente;
        this.servico = servico;
        this.dia = dia;
        this.horario = horario;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getNumeroCliente() {
        return numeroCliente;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public String getServico() {
        return servico;
    }

    public String getDia() {
        return dia;
    }

    public String getHorario() {
        return horario;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    void atualizarStatus(AppointmentStatus status) {
        this.status = status;
        this.atualizadoEm = LocalDateTime.now();
    }
}
