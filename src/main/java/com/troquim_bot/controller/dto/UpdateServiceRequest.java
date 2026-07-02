package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de atualização de Service.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateServiceRequest {

    private String name;
    private String description;
    private Integer durationMinutes;
    private Double price;

    public UpdateServiceRequest() {
    }

    public UpdateServiceRequest(String name, String description, Integer durationMinutes, Double price) {
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Double getPrice() {
        return price;
    }
}
