package com.troquim_bot.appointment;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Appointment.
 * Imutável e validado na criação.
 */
public class AppointmentId {

    private final UUID value;

    public AppointmentId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("AppointmentId não pode ser nulo");
        }
        this.value = value;
    }

    public static AppointmentId from(UUID value) {
        return new AppointmentId(value);
    }

    public static AppointmentId generate() {
        return new AppointmentId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppointmentId that = (AppointmentId) o;
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