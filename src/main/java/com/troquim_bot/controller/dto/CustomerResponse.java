package com.troquim_bot.controller.dto;

import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;

import java.time.LocalDateTime;

/**
 * DTO para resposta de Customer.
 * Usado apenas na camada de apresentação (REST).
 */
public class CustomerResponse {

    private String id;
    private String name;
    private String phone;
    private String notes;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public CustomerResponse() {
    }

    public CustomerResponse(String id, String name, String phone, String notes,
                            String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.notes = notes;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static CustomerResponse from(Customer customer) {
        if (customer == null) {
            return null;
        }

        return new CustomerResponse(
            customer.getId().getValue().toString(),
            customer.getName().getFullName(),
            customer.getPhone().getValue(),
            customer.getNotes(),
            customer.getStatus().name(),
            customer.getCriadoEm(),
            customer.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
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