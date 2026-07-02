package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de criação de Service.
 * Usado apenas na camada de apresentação (REST).
 */
public class CreateServiceRequest {

    private String name;
    private String description;
    private int durationMinutes;
    private double price;

    public CreateServiceRequest() {
    }

    public CreateServiceRequest(String name, String description, int durationMinutes, double price) {
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public double getPrice() {
        return price;
    }
}