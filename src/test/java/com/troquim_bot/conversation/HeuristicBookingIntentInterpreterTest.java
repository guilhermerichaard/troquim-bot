package com.troquim_bot.conversation;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicBookingIntentInterpreterTest {

    private final HeuristicBookingIntentInterpreter interpreter =
            new HeuristicBookingIntentInterpreter();

    @Test
    void extraiServicoDiaEJanelaDepoisDas16() {
        BookingIntent intent = interpreter
                .interpretar("quero manicure sexta depois das 16")
                .orElseThrow();

        assertEquals("sexta", intent.dayQuery());
        assertEquals(LocalTime.of(16, 0), intent.earliestTime());
        assertEquals(LocalTime.of(16, 0), intent.targetTime());
        assertTrue(intent.serviceQuery().toLowerCase().contains("manicure"));
    }

    @Test
    void reconheceMesmoDeSempreSemInventarServico() {
        BookingIntent intent = interpreter
                .interpretar("quero o mesmo de sempre amanhã à tarde")
                .orElseThrow();

        assertTrue(intent.sameAsUsual());
        assertEquals("amanha", intent.dayQuery());
        assertEquals(LocalTime.of(12, 0), intent.earliestTime());
        assertEquals(LocalTime.of(17, 59), intent.latestTime());
    }
}
