package com.troquim_bot.conversation.state;

import java.util.UUID;

public class AppointmentDraft {
    private String commandBase;
    private String servico;
    private String servicoSugerido;
    private String dia;
    private String horario;
    private String nome;
    private boolean confirmado;

    public AppointmentDraft() {
        this.commandBase = UUID.randomUUID().toString();
        this.confirmado = false;
    }

    /** Identidade estável desta tentativa de agendamento, usada na idempotência. */
    public String getCommandBase() {
        return commandBase;
    }

    public void setCommandBase(String commandBase) {
        if (commandBase != null && !commandBase.isBlank()) {
            this.commandBase = commandBase;
        }
    }

    public String getServico() {
        return servico;
    }

    public void setServico(String servico) {
        this.servico = servico;
    }

    public String getServicoSugerido() {
        return servicoSugerido;
    }

    public void setServicoSugerido(String servicoSugerido) {
        this.servicoSugerido = servicoSugerido;
    }

    public String getDia() {
        return dia;
    }

    public void setDia(String dia) {
        this.dia = dia;
    }

    public String getHorario() {
        return horario;
    }

    public void setHorario(String horario) {
        this.horario = horario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    public void setConfirmado(boolean confirmado) {
        this.confirmado = confirmado;
    }

    public boolean isCompleto() {
        return servico != null && !servico.isBlank()
                && dia != null && !dia.isBlank()
                && horario != null && !horario.isBlank()
                && nome != null && !nome.isBlank();
    }

    public String getResumo() {
        return String.format("%s na %s às %s (%s)",
                servico != null ? servico : "serviço não informado",
                dia != null ? dia : "dia não informado",
                horario != null ? horario : "horário não informado",
                nome != null ? nome : "nome não informado");
    }
}