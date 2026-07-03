package com.troquim_bot.controller.dto;

import com.troquim_bot.reservation.Reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO para resposta de Reservation.
 * Usado apenas na camada de apresentação (REST).
 */
public class ReservationResponse {

    private String id;
    private String customerId;
    private String professionalId;
    private String serviceId;
    private String availabilityId;
    private String date;
    private String startTime;
    private String endTime;
    private String expiresAt;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public ReservationResponse() {
    }

    public ReservationResponse(String id, String customerId, String professionalId,
                               String serviceId, String availabilityId,
                               String date, String startTime, String endTime,
                               String expiresAt, String status,
                               LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.expiresAt = expiresAt;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static ReservationResponse from(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        return new ReservationResponse(
            reservation.getId().getValue().toString(),
            reservation.getCustomerId().getValue().toString(),
            reservation.getProfessionalId().getValue().toString(),
            reservation.getServiceId().getValue().toString(),
            reservation.getAvailabilityId().getValue().toString(),
            reservation.getDate().toString(),
            reservation.getStartTime().toString(),
            reservation.getEndTime().toString(),
            reservation.getExpiresAt().toString(),
            reservation.getStatus().name(),
            reservation.getCriadoEm(),
            reservation.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
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