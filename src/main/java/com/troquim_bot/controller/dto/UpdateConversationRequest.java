package com.troquim_bot.controller.dto;

public class UpdateConversationRequest {

    private String selectedServiceId;
    private String selectedProfessionalId;
    private String selectedDate;
    private String selectedStartTime;
    private String selectedEndTime;
    private String reservationId;
    private String appointmentId;

    public UpdateConversationRequest() {
    }

    public UpdateConversationRequest(String selectedServiceId,
                                     String selectedProfessionalId,
                                     String selectedDate,
                                     String selectedStartTime,
                                     String selectedEndTime,
                                     String reservationId,
                                     String appointmentId) {
        this.selectedServiceId = selectedServiceId;
        this.selectedProfessionalId = selectedProfessionalId;
        this.selectedDate = selectedDate;
        this.selectedStartTime = selectedStartTime;
        this.selectedEndTime = selectedEndTime;
        this.reservationId = reservationId;
        this.appointmentId = appointmentId;
    }

    public String getSelectedServiceId() {
        return selectedServiceId;
    }

    public void setSelectedServiceId(String selectedServiceId) {
        this.selectedServiceId = selectedServiceId;
    }

    public String getSelectedProfessionalId() {
        return selectedProfessionalId;
    }

    public void setSelectedProfessionalId(String selectedProfessionalId) {
        this.selectedProfessionalId = selectedProfessionalId;
    }

    public String getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(String selectedDate) {
        this.selectedDate = selectedDate;
    }

    public String getSelectedStartTime() {
        return selectedStartTime;
    }

    public void setSelectedStartTime(String selectedStartTime) {
        this.selectedStartTime = selectedStartTime;
    }

    public String getSelectedEndTime() {
        return selectedEndTime;
    }

    public void setSelectedEndTime(String selectedEndTime) {
        this.selectedEndTime = selectedEndTime;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }
}
