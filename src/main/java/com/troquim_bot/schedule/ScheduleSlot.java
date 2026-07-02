package com.troquim_bot.schedule;

public class ScheduleSlot {
    private final String dia;
    private final String horario;
    private SlotStatus status;
    private String numeroCliente;
    private String observacao;

    public ScheduleSlot(String dia, String horario, SlotStatus status) {
        this.dia = dia;
        this.horario = horario;
        this.status = status;
    }

    public String getDia() {
        return dia;
    }

    public String getHorario() {
        return horario;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public String getNumeroCliente() {
        return numeroCliente;
    }

    public void setNumeroCliente(String numeroCliente) {
        this.numeroCliente = numeroCliente;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}