package com.troquim_bot.controller.dto;

/**
 * DTO para requisição de criação de Reservation.
 * Usado apenas na camada de apresentação (REST).
 */
public class CreateReservationRequest {

    private String customerId;
    private String professionalId;
    private String serviceId;
    private String availabilityId;
    private String date;
    private String startTime;
    private String endTime;
    private String expiresAt;

    public CreateReservationRequest() {
    }

    public CreateReservationRequest(String customerId, String professionalId, String serviceId,
                                    String availabilityId, String date, String startTime,
                                    String endTime, String expiresAt) {
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.expiresAt = expiresAt;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getAvailabilityId() {
        return availabilityId;
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