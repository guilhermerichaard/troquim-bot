package com.troquim_bot.automation;

import java.time.Duration;
import java.time.LocalDateTime;

public final class BookingAutomationPolicy {

    private final boolean reminderEnabled;
    private final int reminderHoursBefore;
    private final int cancellationMinHours;
    private final boolean upsellEnabled;

    public BookingAutomationPolicy(boolean reminderEnabled,
                                   int reminderHoursBefore,
                                   int cancellationMinHours,
                                   boolean upsellEnabled) {
        if (reminderHoursBefore < 1 || reminderHoursBefore > 168) {
            throw new IllegalArgumentException("Lembrete deve ficar entre 1 e 168 horas");
        }
        if (cancellationMinHours < 0 || cancellationMinHours > 720) {
            throw new IllegalArgumentException("Antecedência de cancelamento deve ficar entre 0 e 720 horas");
        }
        this.reminderEnabled = reminderEnabled;
        this.reminderHoursBefore = reminderHoursBefore;
        this.cancellationMinHours = cancellationMinHours;
        this.upsellEnabled = upsellEnabled;
    }

    public static BookingAutomationPolicy padrao() {
        return new BookingAutomationPolicy(true, 24, 0, false);
    }

    public boolean reminderEnabled() { return reminderEnabled; }
    public int reminderHoursBefore() { return reminderHoursBefore; }
    public int cancellationMinHours() { return cancellationMinHours; }
    public boolean upsellEnabled() { return upsellEnabled; }

    public boolean podeCancelar(LocalDateTime inicio, LocalDateTime agora) {
        if (inicio == null || agora == null || !inicio.isAfter(agora)) return false;
        long horas = Duration.between(agora, inicio).toHours();
        return horas >= cancellationMinHours;
    }

    public LocalDateTime instanteDoLembrete(LocalDateTime inicio) {
        if (inicio == null) throw new IllegalArgumentException("Início do agendamento é obrigatório");
        return inicio.minusHours(reminderHoursBefore);
    }
}
