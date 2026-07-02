package com.troquim_bot.common.valueobject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Value Object que representa um slot de tempo (intervalo).
 * Imutável, auto-validado e rico em comportamento.
 */
public class TimeSlot {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final LocalTime start;
    private final LocalTime end;

    public TimeSlot(LocalTime start, LocalTime end) {
        if (start == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (end == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        if (end.isBefore(start) || end.equals(start)) {
            throw new IllegalArgumentException("Horário de fim deve ser posterior ao início");
        }

        this.start = start;
        this.end = end;
    }

    /**
     * Cria TimeSlot com duração padrão de 60 minutos.
     */
    public static TimeSlot of(LocalTime start) {
        return new TimeSlot(start, start.plusMinutes(DEFAULT_DURATION_MINUTES));
    }

    /**
     * Cria TimeSlot com duração customizada.
     */
    public static TimeSlot of(LocalTime start, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duração deve ser maior que zero");
        }
        return new TimeSlot(start, start.plusMinutes(durationMinutes));
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    /**
     * Retorna a duração em minutos.
     */
    public long getDurationMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }

    /**
     * Verifica se este slot se sobrepõe com outro.
     */
    public boolean overlaps(TimeSlot other) {
        return this.start.isBefore(other.end) && this.end.isAfter(other.start);
    }

    /**
     * Verifica se este slot contém um horário específico.
     */
    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * Verifica se este slot é adjacente a outro (termina quando o outro começa).
     */
    public boolean isAdjacentTo(TimeSlot other) {
        return this.end.equals(other.start) || other.end.equals(this.start);
    }

    /**
     * Verifica se este slot está antes de outro.
     */
    public boolean isBefore(TimeSlot other) {
        return this.end.isBefore(other.start) || this.end.equals(other.start);
    }

    /**
     * Verifica se este slot está depois de outro.
     */
    public boolean isAfter(TimeSlot other) {
        return this.start.isAfter(other.end) || this.start.equals(other.end);
    }

    /**
     * Divide o slot em intervalos menores de tamanho fixo.
     */
    public java.util.List<TimeSlot> split(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duração deve ser maior que zero");
        }

        java.util.List<TimeSlot> slots = new java.util.ArrayList<>();
        LocalTime currentStart = start;
        
        while (currentStart.isBefore(end)) {
            LocalTime currentEnd = currentStart.plusMinutes(durationMinutes);
            if (currentEnd.isAfter(end)) {
                currentEnd = end;
            }
            slots.add(new TimeSlot(currentStart, currentEnd));
            currentStart = currentEnd;
        }
        
        return slots;
    }

    /**
     * Verifica se o slot é válido (início < fim).
     */
    public boolean isValid() {
        return start.isBefore(end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeSlot timeSlot = (TimeSlot) o;
        return start.equals(timeSlot.start) && end.equals(timeSlot.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return String.format("%s - %s", start, end);
    }
}