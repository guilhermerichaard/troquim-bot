package com.troquim_bot.application.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberNormalizerTest {

    @Test
    void deveRemoverSufixoWhatsApp() {
        assertEquals("5511916698055", PhoneNumberNormalizer.normalizar("5511916698055@s.whatsapp.net"));
    }

    @Test
    void deveRemoverSufixoCUs() {
        assertEquals("5511916698055", PhoneNumberNormalizer.normalizar("5511916698055@c.us"));
    }

    @Test
    void deveManterNumeroLimp() {
        assertEquals("5511916698055", PhoneNumberNormalizer.normalizar("5511916698055"));
    }

    @Test
    void deveRetornarNullParaNull() {
        assertNull(PhoneNumberNormalizer.normalizar(null));
    }

    @Test
    void deveRemoverSufixoWhatsappNet() {
        assertEquals("5511916698055", PhoneNumberNormalizer.normalizar("5511916698055@whatsapp.net"));
    }

    @Test
    void deveRemoverSufixoComNumeroVazioAntes() {
        assertEquals("", PhoneNumberNormalizer.normalizar("@s.whatsapp.net"));
    }
}