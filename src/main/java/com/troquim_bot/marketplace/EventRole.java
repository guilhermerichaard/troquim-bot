package com.troquim_bot.marketplace;

import java.util.UUID;

public class EventRole {
    private final UUID id;
    private final UUID eventId;
    private final String roleName;
    private final int quantity;
    private int filledQuantity;
    private final Long payCents;
    private final String notes;

    public EventRole(UUID id, UUID eventId, String roleName, int quantity, int filledQuantity, Long payCents, String notes) {
        if (id == null || eventId == null) throw new IllegalArgumentException("Vaga inválida");
        if (roleName == null || roleName.isBlank()) throw new IllegalArgumentException("Função é obrigatória");
        if (quantity <= 0 || filledQuantity < 0 || filledQuantity > quantity) throw new IllegalArgumentException("Quantidade inválida");
        if (payCents != null && payCents < 0) throw new IllegalArgumentException("Cachê não pode ser negativo");
        this.id=id; this.eventId=eventId; this.roleName=roleName.trim(); this.quantity=quantity;
        this.filledQuantity=filledQuantity; this.payCents=payCents; this.notes=notes == null ? null : notes.trim();
    }

    public static EventRole open(UUID eventId, String roleName, int quantity, Long payCents, String notes) {
        return new EventRole(UUID.randomUUID(), eventId, roleName, quantity, 0, payCents, notes);
    }

    public void fillOne() {
        if (isFull()) throw new IllegalStateException("Vaga já preenchida");
        filledQuantity++;
    }

    public boolean isFull(){ return filledQuantity >= quantity; }
    public UUID getId(){return id;} public UUID getEventId(){return eventId;} public String getRoleName(){return roleName;}
    public int getQuantity(){return quantity;} public int getFilledQuantity(){return filledQuantity;}
    public Long getPayCents(){return payCents;} public String getNotes(){return notes;}
}
