package com.troquim_bot.conversation;

import com.troquim_bot.availability.RelogioDoNegocio;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaudacaoDoNegocioTest {

    @Test
    void usaBomDiaAteAntesDoMeioDia() {
        assertEquals("Bom dia", saudacaoEm(5, 0));
        assertEquals("Bom dia", saudacaoEm(11, 59));
    }

    @Test
    void usaBoaTardeDoMeioDiaAteAntesDasDezoito() {
        assertEquals("Boa tarde", saudacaoEm(12, 0));
        assertEquals("Boa tarde", saudacaoEm(17, 59));
    }

    @Test
    void usaBoaNoiteAPartirDasDezoitoEAteAMadrugada() {
        assertEquals("Boa noite", saudacaoEm(18, 0));
        assertEquals("Boa noite", saudacaoEm(23, 59));
        assertEquals("Boa noite", saudacaoEm(0, 30));
    }

    private String saudacaoEm(int hora, int minuto) {
        return new SaudacaoDoNegocio(RelogioDoNegocio.fixo(
                LocalDateTime.of(2026, 9, 21, hora, minuto))).atual();
    }
}
