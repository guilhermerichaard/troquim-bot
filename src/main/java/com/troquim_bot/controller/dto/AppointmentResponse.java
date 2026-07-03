package com.troquim_bot.controller.dto;

import com.troquim_bot.appointment.Appointment;

import java.time.LocalDateTime;

/**
 * DTO para resposta de Appointment.
 * Usado apenas na camada de apresentação (REST).
 */
public class AppointmentResponse {

    private String id;
    private String customerId;
    private String professionalId;
    private String serviceId;
    private String availabilityId;
    private String reservationId;
    private String date;
    private String startTime;
    private String endTime;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public AppointmentResponse() {
    }

    public AppointmentResponse(String id, String customerId, String professionalId,
                               String serviceId, String availabilityId, String reservationId,
                               String date, String startTime, String endTime,
                               String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.reservationId = reservationId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static AppointmentResponse from(Appointment appointment) {
        if (appointment == null) {
            return null;
        }

        return new AppointmentResponse(
            appointment.getId().getValue().toString(),
            appointment.getCustomerId().getValue().toString(),
            appointment.getProfessionalId().getValue().toString(),
            appointment.getServiceId().getValue().toString(),
            appointment.getAvailabilityId().getValue().toString(),
            appointment.getReservationId() != null ? appointment.getReservationId().getValue().toString() : null,
            appointment.getDate().toString(),
            appointment.getStartTime().toString(),
            appointment.getEndTime().toString(),
            appointment.getStatus().name(),
            appointment.getCriadoEm(),
            appointment.getAtualizadoEm()
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

    public String getReservationId() {
        return reservationId;
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