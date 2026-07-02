package com.troquim_bot.controller.dto;

import java.util.Set;

/**
 * DTO para requisição de atualização de Professional.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateProfessionalRequest {

    private String nome;
    private Set<String> especialidades;
    private String telefone;

    public UpdateProfessionalRequest() {
    }

    public UpdateProfessionalRequest(String nome, Set<String> especialidades, String telefone) {
        this.nome = nome;
        this.especialidades = especialidades;
        this.telefone = telefone;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Set<String> getEspecialidades() {
        return especialidades;
    }

    public void setEspecialidades(Set<String> especialidades) {
        this.especialidades = especialidades;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }
}