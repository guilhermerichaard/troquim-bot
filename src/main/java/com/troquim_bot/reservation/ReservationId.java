package com.troquim_bot.reservation;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Reservation.
 * Imutável e validado na criação.
 */
public class ReservationId {

    private final UUID value;

    public ReservationId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ReservationId não pode ser nulo");
        }
        this.value = value;
    }

    public static ReservationId from(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationId that = (ReservationId) o;
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