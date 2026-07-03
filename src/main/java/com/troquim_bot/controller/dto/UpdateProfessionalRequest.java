package com.troquim_bot.controller.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para requisição de atualização de Professional.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateProfessionalRequest {

    @JsonProperty("name")
    private String name;
    
    @JsonProperty("specialties")
    private Set<String> specialties;
    
    @JsonProperty("phone")
    private String phone;

    public UpdateProfessionalRequest() {
    }

    public UpdateProfessionalRequest(String name, Set<String> specialties, String phone) {
        this.name = name;
        this.specialties = specialties;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getSpecialties() {
        return specialties;
    }

    public void setSpecialties(Set<String> specialties) {
        this.specialties = specialties;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}