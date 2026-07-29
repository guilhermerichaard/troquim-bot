package com.troquim_bot.owner.domain;

import java.util.Objects;
import java.util.UUID;

/** Identidade do dono. Surrogate UUID — não deriva de e-mail nem de telefone. */
public final class OwnerUserId {

    private final UUID value;

    private OwnerUserId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static OwnerUserId from(UUID value) {
        return new OwnerUserId(value);
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OwnerUserId other && value.equals(other.value);
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
