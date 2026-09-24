package com.troquim_bot.automation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingAutomationPolicyTest {

    @Test
    void cancellationBoundaryRespectsConfiguredHours() {
        var policy = new BookingAutomationPolicy(true, 24, 24, false);
        var now = LocalDateTime.of(2026, 9, 24, 10, 0);

        assertTrue(policy.podeCancelar(now.plusHours(24), now));
        assertFalse(policy.podeCancelar(now.plusHours(23).plusMinutes(59), now));
    }

    @Test
    void reminderInstantUsesTenantLeadTime() {
        var policy = new BookingAutomationPolicy(true, 18, 0, false);
        var start = LocalDateTime.of(2026, 9, 25, 15, 0);

        assertEquals(LocalDateTime.of(2026, 9, 24, 21, 0),
                policy.instanteDoLembrete(start));
    }

    @Test
    void rejectsUnsafeRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new BookingAutomationPolicy(true, 0, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new BookingAutomationPolicy(true, 169, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new BookingAutomationPolicy(true, 24, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new BookingAutomationPolicy(true, 24, 721, false));
    }
}
