package com.troquim_bot.application.language;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void deveConverterParaLowercase() {
        assertEquals("oi tudo bem", normalizer.normalize("OI TUDO BEM"));
    }

    @Test
    void deveRemoverAcentos() {
        assertEquals("ola amanha as 10h", normalizer.normalize("Olá, amanhã às 10h"));
    }

    @Test
    void deveRemoverAcentosCaracteresEspeciais() {
        assertEquals("aeiou", normalizer.normalize("áéíóú"));
        assertEquals("aeiou", normalizer.normalize("àèìòù"));
        assertEquals("aeiou", normalizer.normalize("âêîôû"));
        assertEquals("aeiou", normalizer.normalize("ãẽĩõũ"));
    }

    @Test
    void deveRemoverEspacosDuplicados() {
        assertEquals("oi tudo bem", normalizer.normalize("oi   tudo    bem"));
    }

    @Test
    void deveRemoverCaracteresRepetidos() {
        assertEquals("oi tudo bem", normalizer.normalize("Ooooi, tudooooo beeem???"));
    }

    @Test
    void deveReduzirCaracteresRepetidosApenasMaisDeDois() {
        assertEquals("oi tudo bem", normalizer.normalize("oiii tudo bem"));
        assertEquals("oii tudo bem", normalizer.normalize("oii tudo bem"));
    }

    @Test
    void deveNormalizarAbreviacoes() {
        assertEquals("obrigado", normalizer.normalize("obg"));
        assertEquals("valeu", normalizer.normalize("vlw"));
        assertEquals("horas", normalizer.normalize("hrs"));
        assertEquals("hora", normalizer.normalize("hr"));
    }

    @Test
    void deveNormalizarAbreviacoesEmFrase() {
        assertEquals("obrigado valeu horas hora", normalizer.normalize("obg vlw hrs hr"));
    }

    @Test
    void deveManterPalavrasNaoAbreviadas() {
        assertEquals("oi tudo bem", normalizer.normalize("oi tudo bem"));
    }

    @Test
    void deveTratarTextoNuloComoVazio() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    void deveTratarTextoVazioComoVazio() {
        assertEquals("", normalizer.normalize(""));
    }

    @Test
    void deveTratarTextoComApenasEspacosComoVazio() {
        assertEquals("", normalizer.normalize("   "));
    }

    @Test
    void deveRemoverCaracteresEspeciais() {
        assertEquals("oi tudo bem", normalizer.normalize("oi! tudo? bem..."));
    }

    @Test
    void deveManterNumeros() {
        assertEquals("10 20 30", normalizer.normalize("10, 20, 30"));
    }

    @Test
    void deveCombinarTodasTransformacoes() {
        assertEquals("obrigado valeu horas", normalizer.normalize("OBG!!! vlw???? hrs..."));
    }

    @Test
    void deveRemoverAccentsEManterCase() {
        String result = normalizer.removeAccents("Olá");
        assertEquals("Ola", result);
    }

    @Test
    void deveTratarNullEmRemoveAccents() {
        assertEquals("", normalizer.removeAccents(null));
    }

    @Test
    void deveTratarNullEmCollapseRepeatedLetters() {
        assertEquals("", normalizer.collapseRepeatedLetters(null));
    }

    @Test
    void deveTratarNullEmNormalizeAbbreviations() {
        assertEquals("", normalizer.normalizeAbbreviations(null));
    }

    @Test
    void deveTratarTextoVazioEmNormalizeAbbreviations() {
        assertEquals("", normalizer.normalizeAbbreviations(""));
    }

    @Test
    void deveTratarTextoComApenasEspacosEmNormalizeAbbreviations() {
        assertEquals("", normalizer.normalizeAbbreviations("   "));
    }
}