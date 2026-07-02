package com.troquim_bot.customer;

import java.time.LocalDateTime;

public class CustomerProfile {

    private final String numero;
    private String nome;
    private String apelido;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private int totalAtendimentos;
    private LocalDateTime ultimoAtendimento;

    public CustomerProfile(String numero) {
        LocalDateTime agora = LocalDateTime.now();
        this.numero = numero;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public String getNumero() {
        return numero;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
        tocar();
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
        tocar();
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public int getTotalAtendimentos() {
        return totalAtendimentos;
    }

    public LocalDateTime getUltimoAtendimento() {
        return ultimoAtendimento;
    }

    public void registrarNovoAtendimento() {
        this.totalAtendimentos++;
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    public void atualizarUltimoAtendimento() {
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
