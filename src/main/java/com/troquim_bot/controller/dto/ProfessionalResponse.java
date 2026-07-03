package com.troquim_bot.controller.dto;

import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.professional.ProfessionalStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para resposta de Professional.
 * Usado apenas na camada de apresentação (REST).
 */
public class ProfessionalResponse {

    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("specialties")
    private Set<String> specialties;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    public ProfessionalResponse() {
    }

    public ProfessionalResponse(String id, String name, Set<String> specialties, String phone,
                                 String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.specialties = specialties;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public Set<String> getSpecialties() {
        return specialties;
    }

    public String getPhone() {
        return phone;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}