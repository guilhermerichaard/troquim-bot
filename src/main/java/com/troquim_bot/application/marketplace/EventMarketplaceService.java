package com.troquim_bot.application.marketplace;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.marketplace.EventApplication;
import com.troquim_bot.marketplace.EventRequest;
import com.troquim_bot.marketplace.EventRole;
import com.troquim_bot.repository.EventMarketplaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventMarketplaceService {
    private final EventMarketplaceRepository repository;

    public EventMarketplaceService(EventMarketplaceRepository repository) { this.repository = repository; }

    @Transactional
    public EventRequest createEvent(BusinessId businessId, String title, String eventType, String city, String venue,
                                    LocalDateTime startsAt, LocalDateTime endsAt, Long budgetCents, List<RoleCommand> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("Evento precisa de ao menos uma vaga");
        EventRequest event = repository.saveEvent(EventRequest.open(businessId,title,eventType,city,venue,startsAt,endsAt,budgetCents));
        for (RoleCommand role : roles) repository.saveRole(EventRole.open(event.getId(), role.roleName(), role.quantity(), role.payCents(), role.notes()));
        return event;
    }

    @Transactional(readOnly = true)
    public List<EventView> listOwner(BusinessId businessId) {
        return repository.findByBusiness(businessId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> opportunities() {
        return repository.findOpenEvents().stream().map(this::view).toList();
    }

    @Transactional
    public EventApplication apply(UUID eventId, UUID roleId, String name, String phone, String message) {
        EventRequest event = repository.findEvent(eventId).orElseThrow(() -> new IllegalArgumentException("Evento não encontrado"));
        if (event.getStatus() != com.troquim_bot.marketplace.EventStatus.OPEN && event.getStatus() != com.troquim_bot.marketplace.EventStatus.MATCHING)
            throw new IllegalStateException("Evento não recebe candidaturas");
        EventRole role = repository.findRole(roleId).filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> new IllegalArgumentException("Vaga não encontrada"));
        if (role.isFull()) throw new IllegalStateException("Vaga já preenchida");
        event.markMatching(); repository.saveEvent(event);
        return repository.saveApplication(EventApplication.apply(eventId, roleId, name, phone, message));
    }

    @Transactional
    public EventApplication accept(BusinessId businessId, UUID eventId, UUID applicationId) {
        EventRequest event = repository.findEvent(eventId)
                .filter(e -> e.getBusinessId().equals(businessId))
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado"));
        EventApplication application = repository.findApplication(applicationId)
                .filter(a -> a.getEventId().equals(eventId))
                .orElseThrow(() -> new IllegalArgumentException("Candidatura não encontrada"));
        EventRole role = repository.findRole(application.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Vaga não encontrada"));
        role.fillOne(); application.accept();
        repository.saveRole(role); repository.saveApplication(application);
        if (repository.allRolesFilled(eventId)) { event.markStaffed(); repository.saveEvent(event); }
        return application;
    }

    private EventView view(EventRequest event) {
        return new EventView(event, repository.findRoles(event.getId()), repository.findApplications(event.getId()));
    }

    public record RoleCommand(String roleName, int quantity, Long payCents, String notes) {}
    public record EventView(EventRequest event, List<EventRole> roles, List<EventApplication> applications) {}
}
