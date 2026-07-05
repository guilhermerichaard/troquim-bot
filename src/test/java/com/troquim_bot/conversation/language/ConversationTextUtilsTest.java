package com.troquim_bot.conversation.language;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTextUtilsTest {

    @Test
    void normalizar_deveRemoverAcentosEConverterParaMinusculas() {
        assertEquals("teste", ConversationTextUtils.normalizar("Teste"));
        assertEquals("teste", ConversationTextUtils.normalizar("tésté"));
        assertEquals("teste com acento", ConversationTextUtils.normalizar("Tésté com acênto"));
        assertEquals("ola mundo", ConversationTextUtils.normalizar("Olá Mundo"));
        assertEquals("teste", ConversationTextUtils.normalizar("TESTE"));
    }

    @Test
    void normalizar_deveLidarComStringsVaziasENulas() {
        assertEquals("", ConversationTextUtils.normalizar(""));
        assertEquals("", ConversationTextUtils.normalizar(null));
    }

    @Test
    void contem_deveRetornarVerdadeiroSeContiverAlgumTermo() {
        assertTrue(ConversationTextUtils.contem("ola mundo", "ola"));
        assertTrue(ConversationTextUtils.contem("bom dia", "dia"));
        assertTrue(ConversationTextUtils.contem("boa tarde", "noite", "tarde"));
        assertTrue(ConversationTextUtils.contem("boa noite", "dia", "noite"));
        assertTrue(ConversationTextUtils.contem("OLA MUNDO", "ola")); // Testa normalização
    }

    @Test
    void contem_deveRetornarFalsoSeNaoContiverNenhumTermo() {
        assertFalse(ConversationTextUtils.contem("ola mundo", "tchau"));
        assertFalse(ConversationTextUtils.contem("bom dia", "tarde", "noite"));
        assertFalse(ConversationTextUtils.contem("", "ola"));
        assertFalse(ConversationTextUtils.contem(null, "ola"));
    }

    @Test
    void contemPalavra_deveRetornarVerdadeiroSeContiverPalavraExata() {
        assertTrue(ConversationTextUtils.contemPalavra("ola mundo", "mundo"));
        assertTrue(ConversationTextUtils.contemPalavra("mundo", "mundo"));
        assertTrue(ConversationTextUtils.contemPalavra("ola mundo bom", "ola"));
        assertTrue(ConversationTextUtils.contemPalavra("bom dia", "dia"));
    }

    @Test
    void contemPalavra_deveRetornarFalsoSeNaoContiverPalavraExata() {
        assertFalse(ConversationTextUtils.contemPalavra("ola mundo", "ola mundo"));
        assertFalse(ConversationTextUtils.contemPalavra("ola mundo", "mun"));
        assertFalse(ConversationTextUtils.contemPalavra("mundo", "mundos"));
        assertFalse(ConversationTextUtils.contemPalavra("", "ola"));
        assertFalse(ConversationTextUtils.contemPalavra(null, "ola"));
        assertFalse(ConversationTextUtils.contemPalavra("pe e mao", "pe"));
        assertFalse(ConversationTextUtils.contemPalavra("pe e mao", "mao"));
    }

    @Test
    void estaVazio_deveRetornarVerdadeiroParaStringsVaziasENulas() {
        assertTrue(ConversationTextUtils.estaVazio(null));
        assertTrue(ConversationTextUtils.estaVazio(""));
        assertTrue(ConversationTextUtils.estaVazio(" "));
        assertTrue(ConversationTextUtils.estaVazio("\t\n"));
    }

    @Test
    void estaVazio_deveRetornarFalsoParaStringsNaoVazias() {
        assertFalse(ConversationTextUtils.estaVazio("ola"));
        assertFalse(ConversationTextUtils.estaVazio(" ola "));
    }
}