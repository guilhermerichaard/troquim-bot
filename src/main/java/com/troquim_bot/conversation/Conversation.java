package com.troquim_bot.conversation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Aggregate Root que representa o estado de uma conversa.
 */
public class Conversation {

    private final ConversationId id;
    private final String customerId;
    private ConversationStep currentStep;
    private String selectedServiceId;
    private String selectedProfessionalId;
    private LocalDate selectedDate;
    private LocalTime selectedStartTime;
    private LocalTime selectedEndTime;
    private String reservationId;
    private String appointmentId;
    private ConversationStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public Conversation(ConversationId id, String customerId) {
        if (id == null) {
            throw new IllegalArgumentException("ConversationId e obrigatorio");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("CustomerId e obrigatorio");
        }

        this.id = id;
        this.customerId = customerId.trim();
        this.currentStep = ConversationStep.IDLE;
        this.status = ConversationStatus.ACTIVE;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    public Conversation(ConversationId id, String customerId, ConversationStep currentStep,
                        String selectedServiceId, String selectedProfessionalId,
                        LocalDate selectedDate, LocalTime selectedStartTime, LocalTime selectedEndTime,
                        String reservationId, String appointmentId, ConversationStatus status,
                        LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ConversationId e obrigatorio");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("CustomerId e obrigatorio");
        }
        if (currentStep == null) {
            throw new IllegalArgumentException("CurrentStep e obrigatorio");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status e obrigatorio");
        }

        this.id = id;
        this.customerId = customerId.trim();
        this.currentStep = currentStep;
        this.selectedServiceId = trimToNull(selectedServiceId);
        this.selectedProfessionalId = trimToNull(selectedProfessionalId);
        this.selectedDate = selectedDate;
        this.selectedStartTime = selectedStartTime;
        this.selectedEndTime = selectedEndTime;
        this.reservationId = trimToNull(reservationId);
        this.appointmentId = trimToNull(appointmentId);
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public ConversationId getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public ConversationStep getCurrentStep() {
        return currentStep;
    }

    public String getSelectedServiceId() {
        return selectedServiceId;
    }

    public String getSelectedProfessionalId() {
        return selectedProfessionalId;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public LocalTime getSelectedStartTime() {
        return selectedStartTime;
    }

    public LocalTime getSelectedEndTime() {
        return selectedEndTime;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean isFinished() {
        return status == ConversationStatus.FINISHED || status == ConversationStatus.CANCELLED;
    }

    public void atualizarCampos(String selectedServiceId,
                                String selectedProfessionalId,
                                LocalDate selectedDate,
                                LocalTime selectedStartTime,
                                LocalTime selectedEndTime,
                                String reservationId,
                                String appointmentId) {
        garantirNaoCancelada();

        if (selectedServiceId != null) {
            this.selectedServiceId = trimToNull(selectedServiceId);
        }
        if (selectedProfessionalId != null) {
            this.selectedProfessionalId = trimToNull(selectedProfessionalId);
        }
        if (selectedDate != null) {
            this.selectedDate = selectedDate;
        }
        if (selectedStartTime != null) {
            this.selectedStartTime = selectedStartTime;
        }
        if (selectedEndTime != null) {
            this.selectedEndTime = selectedEndTime;
        }
        if (reservationId != null) {
            this.reservationId = trimToNull(reservationId);
        }
        if (appointmentId != null) {
            this.appointmentId = trimToNull(appointmentId);
        }

        tocar();
    }

    public void avancar() {
        garantirNaoCancelada();
        if (currentStep == ConversationStep.FINISHED) {
            throw new IllegalStateException("Conversa ja esta finalizada");
        }

        this.currentStep = proximaEtapa(currentStep);
        this.status = currentStep == ConversationStep.FINISHED
            ? ConversationStatus.FINISHED
            : ConversationStatus.ACTIVE;
        tocar();
    }

    public void voltar() {
        garantirNaoCancelada();
        if (currentStep == ConversationStep.IDLE) {
            throw new IllegalStateException("Conversa ja esta no estado inicial");
        }

        this.currentStep = etapaAnterior(currentStep);
        this.status = ConversationStatus.ACTIVE;
        tocar();
    }

    public void resetar() {
        garantirNaoCancelada();
        this.currentStep = ConversationStep.IDLE;
        this.selectedServiceId = null;
        this.selectedProfessionalId = null;
        this.selectedDate = null;
        this.selectedStartTime = null;
        this.selectedEndTime = null;
        this.reservationId = null;
        this.appointmentId = null;
        this.status = ConversationStatus.ACTIVE;
        tocar();
    }

    public void cancelar() {
        if (status == ConversationStatus.CANCELLED) {
            return;
        }
        this.currentStep = ConversationStep.FINISHED;
        this.status = ConversationStatus.CANCELLED;
        tocar();
    }

    private ConversationStep proximaEtapa(ConversationStep step) {
        return switch (step) {
            case IDLE -> ConversationStep.SELECT_SERVICE;
            case SELECT_SERVICE -> ConversationStep.SELECT_PROFESSIONAL;
            case SELECT_PROFESSIONAL -> ConversationStep.SELECT_DATE;
            case SELECT_DATE -> ConversationStep.SELECT_TIME;
            case SELECT_TIME -> ConversationStep.CONFIRMATION;
            case CONFIRMATION -> ConversationStep.FINISHED;
            case FINISHED -> throw new IllegalStateException("Conversa ja esta finalizada");
        };
    }

    private ConversationStep etapaAnterior(ConversationStep step) {
        return switch (step) {
            case IDLE -> throw new IllegalStateException("Conversa ja esta no estado inicial");
            case SELECT_SERVICE -> ConversationStep.IDLE;
            case SELECT_PROFESSIONAL -> ConversationStep.SELECT_SERVICE;
            case SELECT_DATE -> ConversationStep.SELECT_PROFESSIONAL;
            case SELECT_TIME -> ConversationStep.SELECT_DATE;
            case CONFIRMATION -> ConversationStep.SELECT_TIME;
            case FINISHED -> ConversationStep.CONFIRMATION;
        };
    }

    private void garantirNaoCancelada() {
        if (status == ConversationStatus.CANCELLED) {
            throw new IllegalStateException("Conversa cancelada nao pode ser alterada");
        }
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
