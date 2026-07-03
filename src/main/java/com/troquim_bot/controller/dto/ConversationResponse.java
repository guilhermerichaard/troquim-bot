package com.troquim_bot.controller.dto;

import com.troquim_bot.conversation.Conversation;

import java.time.LocalDateTime;

public class ConversationResponse {

    private String id;
    private String customerId;
    private String currentStep;
    private String selectedServiceId;
    private String selectedProfessionalId;
    private String selectedDate;
    private String selectedStartTime;
    private String selectedEndTime;
    private String reservationId;
    private String appointmentId;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public ConversationResponse() {
    }

    public ConversationResponse(String id, String customerId, String currentStep,
                                String selectedServiceId, String selectedProfessionalId,
                                String selectedDate, String selectedStartTime, String selectedEndTime,
                                String reservationId, String appointmentId, String status,
                                LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.customerId = customerId;
        this.currentStep = currentStep;
        this.selectedServiceId = selectedServiceId;
        this.selectedProfessionalId = selectedProfessionalId;
        this.selectedDate = selectedDate;
        this.selectedStartTime = selectedStartTime;
        this.selectedEndTime = selectedEndTime;
        this.reservationId = reservationId;
        this.appointmentId = appointmentId;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static ConversationResponse from(Conversation conversation) {
        if (conversation == null) {
            return null;
        }

        return new ConversationResponse(
            conversation.getId().getValue().toString(),
            conversation.getCustomerId(),
            conversation.getCurrentStep().name(),
            conversation.getSelectedServiceId(),
            conversation.getSelectedProfessionalId(),
            conversation.getSelectedDate() != null ? conversation.getSelectedDate().toString() : null,
            conversation.getSelectedStartTime() != null ? conversation.getSelectedStartTime().toString() : null,
            conversation.getSelectedEndTime() != null ? conversation.getSelectedEndTime().toString() : null,
            conversation.getReservationId(),
            conversation.getAppointmentId(),
            conversation.getStatus().name(),
            conversation.getCriadoEm(),
            conversation.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getSelectedServiceId() {
        return selectedServiceId;
    }

    public String getSelectedProfessionalId() {
        return selectedProfessionalId;
    }

    public String getSelectedDate() {
        return selectedDate;
    }

    public String getSelectedStartTime() {
        return selectedStartTime;
    }

    public String getSelectedEndTime() {
        return selectedEndTime;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getAppointmentId() {
        return appointmentId;
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
