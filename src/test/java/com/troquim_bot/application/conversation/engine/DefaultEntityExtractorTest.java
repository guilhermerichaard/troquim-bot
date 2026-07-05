package com.troquim_bot.application.conversation.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEntityExtractorTest {

    private final DefaultEntityExtractor extractor = new DefaultEntityExtractor();

    @Test
    void deveExtrairServicoDiaEHorarioDaMensagemDeAgendamento() {
        ExtractedEntities entities = extractor.extract("Quero unha segunda às 13");

        assertEquals("unha", entities.service());
        assertEquals("segunda", entities.day());
        assertEquals("13:00", entities.time());
        assertTrue(entities.hasBookingData());
    }

    @Test
    void deveNormalizarHorarioComMinutos() {
        ExtractedEntities entities = extractor.extract("sobrancelha sexta 16:30");

        assertEquals("sobrancelha", entities.service());
        assertEquals("sexta", entities.day());
        assertEquals("16:30", entities.time());
    }

    @Test
    void deveExtrairNomeInformado() {
        ExtractedEntities entities = extractor.extract("meu nome é gui");

        assertEquals("Gui", entities.customerName());
    }
}
