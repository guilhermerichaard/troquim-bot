package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de atualização de Customer.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateCustomerRequest {

    private String name;
    private String phone;
    private String notes;

    public UpdateCustomerRequest() {
    }

    public UpdateCustomerRequest(String name, String phone, String notes) {
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