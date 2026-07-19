package com.troquim_bot.common.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cobre a normalização E.164 usada como chave lógica do Customer (item 4).
 */
class PhoneNumberTest {

    @Test
    void normalizaParaE164ComPrefixoMais() {
        assertEquals("+5511999990001", new PhoneNumber("5511999990001").getE164());
        assertEquals("+5511999990001", new PhoneNumber("+5511999990001").getE164());
    }

    @Test
    void removeSeparadoresAntesDeNormalizar() {
        assertEquals("+5511999990001", new PhoneNumber("55 11 99999-0001").getE164());
        assertEquals("+5511999990001", new PhoneNumber("(55) 11 99999-0001").getE164());
    }

    @Test
    void mesmoNumeroEmFormatosDiferentesTemMesmoE164() {
        assertEquals(
                new PhoneNumber("5511999990001").getE164(),
                new PhoneNumber("+55 11 99999-0001").getE164());
    }
}
