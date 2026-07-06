package com.troquim_bot.controller.dto;

import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para requisição de criação de Professional.
 * Usado apenas na camada de apresentação (REST).
 */
public class CreateProfessionalRequest {

    @JsonProperty("name")
    private String name;
    
    @JsonProperty("specialties")
    private Object specialtiesObj;
    
    @JsonProperty("phone")
    private String phone;

    public CreateProfessionalRequest() {
    }

    public CreateProfessionalRequest(String name, Set<String> specialties, String phone) {
        this.name = name;
        this.specialtiesObj = specialties;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getSpecialties() {
        if (specialtiesObj == null) {
            return Collections.emptySet();
        }
        if (specialtiesObj instanceof Set) {
            return (Set<String>) specialtiesObj;
        }
        if (specialtiesObj instanceof String) {
            return Collections.singleton((String) specialtiesObj);
        }
        if (specialtiesObj instanceof java.util.Collection) {
            return new java.util.HashSet<>((java.util.Collection<String>) specialtiesObj);
        }
        return Collections.singleton(specialtiesObj.toString());
    }

    public void setSpecialties(Set<String> specialties) {
        this.specialtiesObj = specialties;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}