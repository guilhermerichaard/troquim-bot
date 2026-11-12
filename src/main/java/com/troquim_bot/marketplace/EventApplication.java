package com.troquim_bot.marketplace;

import java.time.LocalDateTime;
import java.util.UUID;

public class EventApplication {
    private final UUID id;
    private final UUID eventId;
    private final UUID roleId;
    private final String applicantName;
    private final String applicantPhone;
    private final String message;
    private ApplicationStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EventApplication(UUID id, UUID eventId, UUID roleId, String applicantName, String applicantPhone,
                            String message, ApplicationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null || eventId == null || roleId == null) throw new IllegalArgumentException("Candidatura inválida");
        if (applicantName == null || applicantName.isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        if (applicantPhone == null || applicantPhone.isBlank()) throw new IllegalArgumentException("Telefone é obrigatório");
        this.id=id; this.eventId=eventId; this.roleId=roleId; this.applicantName=applicantName.trim();
        this.applicantPhone=applicantPhone.trim(); this.message=message == null ? null : message.trim();
        this.status=status == null ? ApplicationStatus.APPLIED : status;
        this.createdAt=createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt=updatedAt == null ? this.createdAt : updatedAt;
    }

    public static EventApplication apply(UUID eventId, UUID roleId, String name, String phone, String message) {
        return new EventApplication(UUID.randomUUID(), eventId, roleId, name, phone, message,
                ApplicationStatus.APPLIED, LocalDateTime.now(), LocalDateTime.now());
    }

    public void accept() {
        if (status != ApplicationStatus.APPLIED) throw new IllegalStateException("Somente candidatura pendente pode ser aceita");
        status=ApplicationStatus.ACCEPTED; updatedAt=LocalDateTime.now();
    }

    public UUID getId(){return id;} public UUID getEventId(){return eventId;} public UUID getRoleId(){return roleId;}
    public String getApplicantName(){return applicantName;} public String getApplicantPhone(){return applicantPhone;}
    public String getMessage(){return message;} public ApplicationStatus getStatus(){return status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
