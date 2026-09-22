package com.troquim_bot.waitlist;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Pedido persistido de aviso quando um slot compatível voltar a existir.
 *
 * A waitlist não reserva nada. Quando um slot abre, o cliente é avisado e a confirmação
 * normal continua revalidando a disponibilidade no Domain.
 */
public class WaitlistEntry {

    private final UUID id;
    private final BusinessId businessId;
    private final String phoneE164;
    private final ServiceId serviceId;
    private final ProfessionalId professionalId;
    private final LocalDate requestedDate;
    private final LocalTime earliestTime;
    private final LocalTime latestTime;
    private WaitlistStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime notifiedAt;

    public WaitlistEntry(UUID id,
                         BusinessId businessId,
                         String phoneE164,
                         ServiceId serviceId,
                         ProfessionalId professionalId,
                         LocalDate requestedDate,
                         LocalTime earliestTime,
                         LocalTime latestTime,
                         WaitlistStatus status,
                         LocalDateTime createdAt,
                         LocalDateTime notifiedAt) {
        if (id == null || businessId == null || phoneE164 == null || phoneE164.isBlank()
                || serviceId == null || professionalId == null || status == null || createdAt == null) {
            throw new IllegalArgumentException("Waitlist incompleta");
        }
        if (earliestTime != null && latestTime != null && latestTime.isBefore(earliestTime)) {
            throw new IllegalArgumentException("Janela de horário inválida");
        }
        this.id = id;
        this.businessId = businessId;
        this.phoneE164 = phoneE164;
        this.serviceId = serviceId;
        this.professionalId = professionalId;
        this.requestedDate = requestedDate;
        this.earliestTime = earliestTime;
        this.latestTime = latestTime;
        this.status = status;
        this.createdAt = createdAt;
        this.notifiedAt = notifiedAt;
    }

    public static WaitlistEntry active(BusinessId businessId,
                                       String phoneE164,
                                       ServiceId serviceId,
                                       ProfessionalId professionalId,
                                       LocalDate requestedDate,
                                       LocalTime earliestTime,
                                       LocalTime latestTime) {
        return new WaitlistEntry(UUID.randomUUID(), businessId, phoneE164, serviceId,
                professionalId, requestedDate, earliestTime, latestTime,
                WaitlistStatus.ACTIVE, LocalDateTime.now(), null);
    }

    public boolean matches(BusinessId businessId,
                           ServiceId serviceId,
                           ProfessionalId professionalId,
                           LocalDate date,
                           LocalTime time) {
        if (status != WaitlistStatus.ACTIVE
                || !this.businessId.equals(businessId)
                || !this.serviceId.equals(serviceId)
                || !this.professionalId.equals(professionalId)) {
            return false;
        }
        if (requestedDate != null && !requestedDate.equals(date)) {
            return false;
        }
        if (earliestTime != null && time.isBefore(earliestTime)) {
            return false;
        }
        return latestTime == null || !time.isAfter(latestTime);
    }

    public void markNotified() {
        if (status != WaitlistStatus.ACTIVE) {
            throw new IllegalStateException("Somente espera ativa pode ser notificada");
        }
        status = WaitlistStatus.NOTIFIED;
        notifiedAt = LocalDateTime.now();
    }

    public void reactivate() {
        if (status != WaitlistStatus.NOTIFIED) {
            throw new IllegalStateException("Somente espera notificada pode ser reativada");
        }
        status = WaitlistStatus.ACTIVE;
        notifiedAt = null;
    }

    public UUID getId() { return id; }
    public BusinessId getBusinessId() { return businessId; }
    public String getPhoneE164() { return phoneE164; }
    public ServiceId getServiceId() { return serviceId; }
    public ProfessionalId getProfessionalId() { return professionalId; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public LocalTime getEarliestTime() { return earliestTime; }
    public LocalTime getLatestTime() { return latestTime; }
    public WaitlistStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
}
