package com.troquim_bot.conversation.state;

public class ConversationState {

    private final String numero;
    private ConversationStep step;
    private String servico;
    private String dia;
    private String horario;
    private String nome;
    private String ultimaPergunta;

    public ConversationState(String numero) {
        this.numero = numero;
        this.step = ConversationStep.INICIO;
    }

    public String getNumero() {
        return numero;
    }

    public ConversationStep getStep() {
        return step;
    }

    public void setStep(ConversationStep step) {
        this.step = step;
    }

    public String getServico() {
        return servico;
    }

    public void setServico(String servico) {
        this.servico = servico;
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

    public String getUltimaPergunta() {
        return ultimaPergunta;
    }

    public void setUltimaPergunta(String ultimaPergunta) {
        this.ultimaPergunta = ultimaPergunta;
    }
}
