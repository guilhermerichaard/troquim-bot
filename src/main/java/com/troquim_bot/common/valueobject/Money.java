package com.troquim_bot.common.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object que representa dinheiro (valor monetário).
 * Imutável, auto-validado e rico em comportamento.
 * Usa BigDecimal para precisão decimal.
 */
public class Money {

    private static final int DEFAULT_SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Valor não pode ser nulo");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Moeda é obrigatória");
        }

        this.amount = amount.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
        this.currency = currency.trim().toUpperCase();
    }

    /**
     * Cria Money com valor em centavos (inteiro).
     */
    public static Money ofCents(long cents, String currency) {
        if (cents < 0) {
            throw new IllegalArgumentException("Valor em centavos não pode ser negativo");
        }
        BigDecimal amount = new BigDecimal(cents).divide(HUNDRED, DEFAULT_SCALE, RoundingMode.HALF_UP);
        return new Money(amount, currency);
    }

    /**
     * Cria Money a partir de valor decimal (ex: 10.50).
     */
    public static Money of(double amount, String currency) {
        if (amount < 0) {
            throw new IllegalArgumentException("Valor não pode ser negativo");
        }
        return new Money(new BigDecimal(amount), currency);
    }

    /**
     * Cria Money zerado.
     */
    public static Money zero(String currency) {
        return new Money(ZERO, currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Retorna valor em centavos (inteiro).
     */
    public long getCents() {
        return amount.multiply(HUNDRED).longValue();
    }

    /**
     * Soma dois valores Money (mesma moeda).
     */
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtrai dois valores Money (mesma moeda).
     */
    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Multiplica por um fator.
     */
    public Money multiply(double factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Fator não pode ser negativo");
        }
        return new Money(this.amount.multiply(new BigDecimal(factor)), this.currency);
    }

    /**
     * Verifica se é zero.
     */
    public boolean isZero() {
        return amount.compareTo(ZERO) == 0;
    }

    /**
     * Verifica se é positivo.
     */
    public boolean isPositive() {
        return amount.compareTo(ZERO) > 0;
    }

    /**
     * Verifica se é negativo.
     */
    public boolean isNegative() {
        return amount.compareTo(ZERO) < 0;
    }

    /**
     * Compara com outro Money (mesma moeda).
     */
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    /**
     * Verifica se é maior que outro Money.
     */
    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    /**
     * Verifica se é menor que outro Money.
     */
    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    private void validateSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                String.format("Moedas diferentes: %s vs %s", this.currency, other.currency)
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return String.format("%s %.2f", currency, amount);
    }
}