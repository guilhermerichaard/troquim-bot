package com.troquim_bot.availability;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Availability.
 * Imutável e validado na criação.
 */
public class AvailabilityId {

    private final UUID value;

    public AvailabilityId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("AvailabilityId não pode ser nulo");
        }
        this.value = value;
    }

    public static AvailabilityId from(UUID value) {
        return new AvailabilityId(value);
    }

    public static AvailabilityId generate() {
        return new AvailabilityId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvailabilityId that = (AvailabilityId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}