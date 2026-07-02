package com.troquim_bot.professional;

import java.util.UUID;

/**
 * Value Object que representa o identificador único do Professional.
 * Imutável e validado na criação.
 */
public class ProfessionalId {

    private final UUID value;

    public ProfessionalId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ProfessionalId não pode ser nulo");
        }
        this.value = value;
    }

    public static ProfessionalId from(UUID value) {
        return new ProfessionalId(value);
    }

    public static ProfessionalId generate() {
        return new ProfessionalId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfessionalId that = (ProfessionalId) o;
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