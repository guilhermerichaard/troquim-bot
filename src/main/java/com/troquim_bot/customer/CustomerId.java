package com.troquim_bot.customer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Value Object que representa o identificador único do Customer.
 * Imutável e validado na criação.
 */
public class CustomerId {

    private final UUID value;

    public CustomerId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("CustomerId não pode ser nulo");
        }
        this.value = value;
    }

    public static CustomerId from(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId fromPhone(String phone) {
        return new CustomerId(UUID.nameUUIDFromBytes(("customer:" + safeValue(phone)).getBytes(StandardCharsets.UTF_8)));
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerId customerId = (CustomerId) o;
        return value.equals(customerId.value);
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