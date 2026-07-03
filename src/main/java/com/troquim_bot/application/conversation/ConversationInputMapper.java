package com.troquim_bot.application.conversation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Component
public class ConversationInputMapper {

    public String customerId(String value) {
        return normalizeUuid(value, "CustomerId");
    }

    public ConversationUpdate update(String selectedServiceId,
                                     String selectedProfessionalId,
                                     String selectedDate,
                                     String selectedStartTime,
                                     String selectedEndTime,
                                     String reservationId,
                                     String appointmentId) {
        return new ConversationUpdate(
            normalizeOptionalUuid(selectedServiceId, "SelectedServiceId"),
            normalizeOptionalUuid(selectedProfessionalId, "SelectedProfessionalId"),
            parseOptionalDate(selectedDate, "SelectedDate"),
            parseOptionalTime(selectedStartTime, "SelectedStartTime"),
            parseOptionalTime(selectedEndTime, "SelectedEndTime"),
            normalizeOptionalUuid(reservationId, "ReservationId"),
            normalizeOptionalUuid(appointmentId, "AppointmentId")
        );
    }

    private String normalizeUuid(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " e obrigatorio");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " deve ser um UUID valido", e);
        }
    }

    private String normalizeOptionalUuid(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.trim().isEmpty()) {
            return "";
        }
        return normalizeUuid(value, fieldName);
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " deve estar no formato yyyy-MM-dd", e);
        }
    }

    private LocalTime parseOptionalTime(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " deve estar no formato HH:mm", e);
        }
    }

    public record ConversationUpdate(String selectedServiceId,
                                     String selectedProfessionalId,
                                     LocalDate selectedDate,
                                     LocalTime selectedStartTime,
                                     LocalTime selectedEndTime,
                                     String reservationId,
                                     String appointmentId) {
    }
}
