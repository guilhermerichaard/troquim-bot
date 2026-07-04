package com.troquim_bot.application.language;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessDictionaryTest {

    @Test
    void deveCanonicalizarTextoComAbreviaturas() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce tem hora hoje no whatsapp", dictionary.canonicalize("vc tem hr hj no zap"));
    }

    @Test
    void deveCanonicalizarTextoComAbreviaturasDeServicos() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("agendar sobrancelha", dictionary.canonicalize("agendr sobr"));
    }

    @Test
    void deveCanonicalizarTextoComAbreviaturasDeServicosEsteticos() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("manicure unha cabelo maquiagem estetica depilacao massagem",
                     dictionary.canonicalize("manicuri unhas cabelo maquiag estetic depil massag"));
    }

    @Test
    void deveManterPalavrasNaoCadastradas() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("ola mundo", dictionary.canonicalize("ola mundo"));
    }

    @Test
    void deveCanonicalizarTextoMisto() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce que agendar manicure hoje", dictionary.canonicalize("vc q agendar manicuri hj"));
    }

    @Test
    void deveRetornarVazioParaTextoNulo() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("", dictionary.canonicalize(null));
    }

    @Test
    void deveRetornarVazioParaTextoVazio() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("", dictionary.canonicalize(""));
    }

    @Test
    void deveRetornarVazioParaTextoComApenasEspacos() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("", dictionary.canonicalize("   "));
    }

    @Test
    void deveBuscarEntradaExistente() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertTrue(dictionary.lookup("vc").isPresent());
        assertEquals("voce", dictionary.lookup("vc").orElseThrow());
    }

    @Test
    void deveRetornarOptionalVazioParaEntradaInexistente() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertTrue(dictionary.lookup("xyz").isEmpty());
    }

    @Test
    void deveRetornarOptionalVazioParaTokenNulo() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertTrue(dictionary.lookup(null).isEmpty());
    }

    @Test
    void deveRetornarTodasEntradas() {
        BusinessDictionary dictionary = new BusinessDictionary();
        Map<String, String> entries = dictionary.entries();
        assertFalse(entries.isEmpty());
        assertEquals(30, entries.size());
    }

    @Test
    void deveTerExatamenteTrintaEntradasNoDicionarioPadrao() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals(30, dictionary.size());
    }

    @Test
    void deveConterTodasAbreviaturasEsperadas() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce", dictionary.lookup("vc").orElseThrow());
        assertEquals("voces", dictionary.lookup("vcs").orElseThrow());
        assertEquals("tambem", dictionary.lookup("tb").orElseThrow());
        assertEquals("tambem", dictionary.lookup("tbm").orElseThrow());
        assertEquals("tudo", dictionary.lookup("td").orElseThrow());
        assertEquals("porque", dictionary.lookup("pq").orElseThrow());
        assertEquals("que", dictionary.lookup("q").orElseThrow());
        assertEquals("hoje", dictionary.lookup("hj").orElseThrow());
        assertEquals("mensagem", dictionary.lookup("msg").orElseThrow());
        assertEquals("whatsapp", dictionary.lookup("zap").orElseThrow());
        assertEquals("whatsapp", dictionary.lookup("whats").orElseThrow());
        assertEquals("hora", dictionary.lookup("hr").orElseThrow());
        assertEquals("horas", dictionary.lookup("hrs").orElseThrow());
        assertEquals("minuto", dictionary.lookup("min").orElseThrow());
        assertEquals("minutos", dictionary.lookup("mins").orElseThrow());
        assertEquals("beleza", dictionary.lookup("blz").orElseThrow());
        assertEquals("obrigado", dictionary.lookup("obg").orElseThrow());
        assertEquals("por favor", dictionary.lookup("pfv").orElseThrow());
        assertEquals("agendar", dictionary.lookup("ag").orElseThrow());
        assertEquals("sobrancelha", dictionary.lookup("sobr").orElseThrow());
        assertEquals("agendar", dictionary.lookup("agendr").orElseThrow());
        assertEquals("agendar", dictionary.lookup("agend").orElseThrow());
        assertEquals("segunda", dictionary.lookup("segnda").orElseThrow());
        assertEquals("manicure", dictionary.lookup("manicuri").orElseThrow());
        assertEquals("unha", dictionary.lookup("unhas").orElseThrow());
        assertEquals("cabelo", dictionary.lookup("cabelo").orElseThrow());
        assertEquals("maquiagem", dictionary.lookup("maquiag").orElseThrow());
        assertEquals("estetica", dictionary.lookup("estetic").orElseThrow());
        assertEquals("depilacao", dictionary.lookup("depil").orElseThrow());
        assertEquals("massagem", dictionary.lookup("massag").orElseThrow());
    }

    @Test
    void deveRejeitarDicionarioNulo() {
        assertThrows(NullPointerException.class, () -> new BusinessDictionary(null));
    }

    @Test
    void deveRejeitarDicionarioComMaisDeTrintaEntradas() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 31; i++) {
            entries.put("k" + i, "v" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> new BusinessDictionary(entries));
    }

    @Test
    void deveAceitarDicionarioComExatamenteTrintaEntradas() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            entries.put("k" + i, "v" + i);
        }

        BusinessDictionary dictionary = new BusinessDictionary(entries);
        assertEquals(30, dictionary.size());
    }

    @Test
    void deveAceitarDicionarioVazio() {
        BusinessDictionary dictionary = new BusinessDictionary(Map.of());
        assertEquals(0, dictionary.size());
    }

    @Test
    void deveCanonicalizarMultiplasAbreviaturasEmSequencia() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce tambem tudo porque que hoje",
                     dictionary.canonicalize("vc tb td pq q hj"));
    }

    @Test
    void devePreservarOrdemDasPalavras() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce ola mundo", dictionary.canonicalize("vc ola mundo"));
    }

    @Test
    void deveNormalizarEspacosMultiplosNaEntrada() {
        BusinessDictionary dictionary = new BusinessDictionary();
        assertEquals("voce ola", dictionary.canonicalize("vc   ola"));
    }
}