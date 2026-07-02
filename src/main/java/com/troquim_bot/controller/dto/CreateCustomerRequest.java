package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de criação de Customer.
 * Usado apenas na camada de apresentação (REST).
 */
public class CreateCustomerRequest {

    private String name;
    private String phone;
    private String notes;

    public CreateCustomerRequest() {
    }

    public CreateCustomerRequest(String name, String phone, String notes) {
        this.name = name;
        this.phone = phone;
        this.notes = notes;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }
}