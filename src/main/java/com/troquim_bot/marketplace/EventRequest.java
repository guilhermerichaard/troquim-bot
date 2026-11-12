package com.troquim_bot.marketplace;

import com.troquim_bot.business.BusinessId;
import java.time.LocalDateTime;
import java.util.UUID;

public class EventRequest {
    private final UUID id;
    private final BusinessId businessId;
    private final String title;
    private final String eventType;
    private final String city;
    private final String venue;
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final Long budgetCents;
    private EventStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EventRequest(UUID id, BusinessId businessId, String title, String eventType,
                        String city, String venue, LocalDateTime startsAt, LocalDateTime endsAt,
                        Long budgetCents, EventStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null || businessId == null) throw new IllegalArgumentException("Evento e contratante são obrigatórios");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Título do evento é obrigatório");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("Tipo do evento é obrigatório");
        if (city == null || city.isBlank()) throw new IllegalArgumentException("Cidade é obrigatória");
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) throw new IllegalArgumentException("Período do evento é inválido");
        if (budgetCents != null && budgetCents < 0) throw new IllegalArgumentException("Orçamento não pode ser negativo");
        this.id=id; this.businessId=businessId; this.title=title.trim(); this.eventType=eventType.trim();
        this.city=city.trim(); this.venue=venue == null ? null : venue.trim(); this.startsAt=startsAt; this.endsAt=endsAt;
        this.budgetCents=budgetCents; this.status=status == null ? EventStatus.DRAFT : status;
        this.createdAt=createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt=updatedAt == null ? this.createdAt : updatedAt;
    }

    public static EventRequest open(BusinessId businessId, String title, String eventType, String city,
                                    String venue, LocalDateTime startsAt, LocalDateTime endsAt, Long budgetCents) {
        return new EventRequest(UUID.randomUUID(), businessId, title, eventType, city, venue, startsAt, endsAt,
                budgetCents, EventStatus.OPEN, LocalDateTime.now(), LocalDateTime.now());
    }

    public void markMatching() {
        if (status != EventStatus.OPEN && status != EventStatus.MATCHING) throw new IllegalStateException("Evento não aceita matching");
        status = EventStatus.MATCHING; updatedAt = LocalDateTime.now();
    }

    public void markStaffed() {
        if (status == EventStatus.CANCELED || status == EventStatus.COMPLETED) throw new IllegalStateException("Evento encerrado");
        status = EventStatus.STAFFED; updatedAt = LocalDateTime.now();
    }

    public UUID getId(){return id;} public BusinessId getBusinessId(){return businessId;}
    public String getTitle(){return title;} public String getEventType(){return eventType;}
    public String getCity(){return city;} public String getVenue(){return venue;}
    public LocalDateTime getStartsAt(){return startsAt;} public LocalDateTime getEndsAt(){return endsAt;}
    public Long getBudgetCents(){return budgetCents;} public EventStatus getStatus(){return status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
