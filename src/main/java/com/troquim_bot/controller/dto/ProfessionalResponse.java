package com.troquim_bot.controller.dto;

import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.professional.ProfessionalStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO para resposta de Professional.
 * Usado apenas na camada de apresentação (REST).
 */
public class ProfessionalResponse {

    private String id;
    private String nome;
    private Set<String> especialidades;
    private String telefone;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public ProfessionalResponse() {
    }

    public ProfessionalResponse(String id, String nome, Set<String> especialidades, String telefone,
                                String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.especialidades = especialidades;
        this.telefone = telefone;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static ProfessionalResponse from(Professional professional) {
        if (professional == null) {
            return null;
        }

        return new ProfessionalResponse(
            professional.getId().getValue().toString(),
            professional.getNome(),
            professional.getEspecialidades(),
            professional.getTelefone(),
            professional.getStatus().name(),
            professional.getCriadoEm(),
            professional.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Set<String> getEspecialidades() {
        return especialidades;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}