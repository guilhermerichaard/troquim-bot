package com.troquim_bot.conversation;

import java.time.LocalTime;

/**
 * Interpretação de linguagem natural para agendamento.
 *
 * Não contém ServiceId, ProfessionalId nem qualquer decisão de agenda. É apenas o que o
 * cliente parece ter pedido; catálogo e disponibilidade continuam decididos pelo Domain.
 */
public record BookingIntent(
        String serviceQuery,
        String dayQuery,
        LocalTime earliestTime,
        LocalTime latestTime,
        LocalTime targetTime,
        boolean sameAsUsual) {

    public BookingIntent {
        serviceQuery = serviceQuery == null ? "" : serviceQuery.trim();
        dayQuery = dayQuery == null ? "" : dayQuery.trim();
    }

    public boolean hasDayPreference() {
        return !dayQuery.isBlank();
    }

    public boolean hasTimePreference() {
        return earliestTime != null || latestTime != null || targetTime != null;
    }

    public boolean accepts(LocalTime time) {
        if (time == null) {
            return false;
        }
        if (earliestTime != null && time.isBefore(earliestTime)) {
            return false;
        }
        if (latestTime != null && time.isAfter(latestTime)) {
            return false;
        }
        return true;
    }
}
