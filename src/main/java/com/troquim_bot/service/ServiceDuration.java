package com.troquim_bot.service;

import java.util.Objects;

/**
 * Value Object que representa a duração de um serviço.
 * Imutável, auto-validado e rico em comportamento.
 */
public class ServiceDuration {

    private static final int MIN_DURATION_MINUTES = 1;

    private final int minutes;

    public ServiceDuration(int minutes) {
        if (minutes < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException("Duração deve ser maior que zero");
        }
        this.minutes = minutes;
    }

    /**
     * Cria ServiceDuration a partir de minutos.
     */
    public static ServiceDuration ofMinutes(int minutes) {
        return new ServiceDuration(minutes);
    }

    /**
     * Cria ServiceDuration a partir de horas.
     */
    public static ServiceDuration ofHours(int hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Horas deve ser maior que zero");
        }
        return new ServiceDuration(hours * 60);
    }

    /**
     * Cria ServiceDuration a partir de horas e minutos.
     */
    public static ServiceDuration of(int hours, int minutes) {
        if (hours < 0 || minutes < 0) {
            throw new IllegalArgumentException("Horas e minutos não podem ser negativos");
        }
        int totalMinutes = hours * 60 + minutes;
        if (totalMinutes < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException("Duração total deve ser maior que zero");
        }
        return new ServiceDuration(totalMinutes);
    }

    public int getMinutes() {
        return minutes;
    }

    /**
     * Retorna a duração em horas (inteiro).
     */
    public int getHours() {
        return minutes / 60;
    }

    /**
     * Retorna os minutos restantes (0-59).
     */
    public int getRemainingMinutes() {
        return minutes % 60;
    }

    /**
     * Retorna a duração formatada: "1h 30min" ou "30min".
     */
    public String getFormatted() {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            }
            return hours + "h " + remainingMinutes + "min";
        }
        return minutes + "min";
    }

    /**
     * Soma duas durações.
     */
    public ServiceDuration add(ServiceDuration other) {
        return new ServiceDuration(this.minutes + other.minutes);
    }

    /**
     * Verifica se a duração é maior que outra.
     */
    public boolean isGreaterThan(ServiceDuration other) {
        return this.minutes > other.minutes;
    }

    /**
     * Verifica se a duração é menor que outra.
     */
    public boolean isLessThan(ServiceDuration other) {
        return this.minutes < other.minutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceDuration that = (ServiceDuration) o;
        return minutes == that.minutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minutes);
    }

    @Override
    public String toString() {
        return minutes + "min";
    }
}