package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de atualização de Availability.
 * Usado apenas na camada de apresentação (REST).
 */
public class UpdateAvailabilityRequest {

    private String professionalId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;

    public UpdateAvailabilityRequest() {
    }

    public UpdateAvailabilityRequest(String professionalId, String dayOfWeek, String startTime, String endTime) {
        this.professionalId = professionalId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }
}