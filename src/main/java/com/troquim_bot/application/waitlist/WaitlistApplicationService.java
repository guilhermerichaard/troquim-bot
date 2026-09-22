package com.troquim_bot.application.waitlist;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.WaitlistRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.waitlist.WaitlistEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Orquestra waitlist sem criar reserva implícita.
 *
 * Fairness: quando um slot abre, somente a espera ativa mais antiga compatível recebe
 * o primeiro aviso. Se o transporte não conseguiu enviar, a entrada permanece ACTIVE.
 */
@Service
public class WaitlistApplicationService {

    private final WaitlistRepository repository;
    private final List<WaitlistNotificationGateway> notificationGateways;

    public WaitlistApplicationService(WaitlistRepository repository,
                                      List<WaitlistNotificationGateway> notificationGateways) {
        this.repository = repository;
        this.notificationGateways = List.copyOf(notificationGateways);
    }

    public WaitlistEntry join(BusinessId businessId,
                              String phone,
                              ServiceId serviceId,
                              ProfessionalId professionalId,
                              LocalDate requestedDate,
                              LocalTime earliestTime,
                              LocalTime latestTime) {
        String e164 = new PhoneNumber(phone).getE164();

        var existente = repository.findActiveRequest(
                businessId, e164, serviceId, professionalId,
                requestedDate, earliestTime, latestTime);
        if (existente.isPresent()) {
            return existente.get();
        }

        return repository.save(WaitlistEntry.active(
                businessId, e164, serviceId, professionalId,
                requestedDate, earliestTime, latestTime));
    }

    public java.util.Optional<WaitlistEntry> claim(java.util.UUID id, String phone) {
        if (id == null || phone == null || phone.isBlank()) {
            return java.util.Optional.empty();
        }
        String e164;
        try {
            e164 = new PhoneNumber(phone).getE164();
        } catch (IllegalArgumentException invalido) {
            return java.util.Optional.empty();
        }
        return repository.findById(id)
                .filter(entry -> entry.getStatus()
                        == com.troquim_bot.waitlist.WaitlistStatus.NOTIFIED)
                .filter(entry -> entry.getPhoneE164().equals(e164));
    }

    public void reactivate(java.util.UUID id, String phone) {
        claim(id, phone).ifPresent(entry -> {
            entry.reactivate();
            repository.save(entry);
        });
    }

    public boolean slotReleased(BusinessId businessId,
                                ServiceId serviceId,
                                ProfessionalId professionalId,
                                String serviceName,
                                LocalDate date,
                                LocalTime time) {
        WaitlistEntry next = repository.findActiveByBusiness(businessId).stream()
                .filter(entry -> entry.matches(
                        businessId, serviceId, professionalId, date, time))
                .min(Comparator.comparing(WaitlistEntry::getCreatedAt))
                .orElse(null);

        if (next == null) {
            return false;
        }

        for (WaitlistNotificationGateway gateway : notificationGateways) {
            if (gateway.notifySlotAvailable(next.getId(), next.getPhoneE164(), serviceName, date, time)) {
                next.markNotified();
                repository.save(next);
                return true;
            }
        }
        return false;
    }
}
