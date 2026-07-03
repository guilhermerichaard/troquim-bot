package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de atualização de Reservation.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateReservationRequest {

    private String date;
    private String startTime;
    private String endTime;
    private String expiresAt;

    public UpdateReservationRequest() {
    }

    public UpdateReservationRequest(String date, String startTime, String endTime, String expiresAt) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.expiresAt = expiresAt;
    }

    public String getDate() {
        return date;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}