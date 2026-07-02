package com.troquim_bot.service;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Service.
 * Imutável e validado na criação.
 */
public class ServiceId {

    private final UUID value;

    public ServiceId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ServiceId não pode ser nulo");
        }
        this.value = value;
    }

    public static ServiceId from(UUID value) {
        return new ServiceId(value);
    }

    public static ServiceId generate() {
        return new ServiceId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceId serviceId = (ServiceId) o;
        return value.equals(serviceId.value);
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