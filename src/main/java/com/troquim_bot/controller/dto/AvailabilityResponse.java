package com.troquim_bot.controller.dto;

import com.troquim_bot.availability.Availability;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO para resposta de Availability.
 * Usado apenas na camada de apresentação (REST).
 */
public class AvailabilityResponse {

    private String id;
    private String professionalId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public AvailabilityResponse() {
    }

    public AvailabilityResponse(String id, String professionalId, String dayOfWeek,
                                String startTime, String endTime, String status,
                                LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.professionalId = professionalId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static AvailabilityResponse from(Availability availability) {
        if (availability == null) {
            return null;
        }

        return new AvailabilityResponse(
            availability.getId().getValue().toString(),
            availability.getProfessionalId().getValue().toString(),
            availability.getDayOfWeek().name(),
            availability.getStartTime().toString(),
            availability.getEndTime().toString(),
            availability.getStatus().name(),
            availability.getCriadoEm(),
            availability.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
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