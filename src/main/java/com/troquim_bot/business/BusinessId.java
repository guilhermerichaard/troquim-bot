package com.troquim_bot.business;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Business.
 * Imutável e validado na criação.
 */
public class BusinessId {

    private final UUID value;

    public BusinessId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("BusinessId não pode ser nulo");
        }
        this.value = value;
    }

    public static BusinessId from(UUID value) {
        return new BusinessId(value);
    }

    public static BusinessId generate() {
        return new BusinessId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessId businessId = (BusinessId) o;
        return value.equals(businessId.value);
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