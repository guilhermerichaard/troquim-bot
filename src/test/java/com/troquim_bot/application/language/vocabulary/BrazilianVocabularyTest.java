package com.troquim_bot.application.language.vocabulary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrazilianVocabularyTest {

    private BrazilianVocabulary vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = new BrazilianVocabulary();
    }

    @Test
    void shouldInitializeWithCorrectSize() {
        Map<String, String> vocab = vocabulary.getVocabulary();
        assertTrue(vocab.size() <= 80, "Vocabulary should have at most 80 entries, but has: " + vocab.size());
        assertTrue(vocab.size() > 0, "Vocabulary should not be empty");
    }

    @Test
    void shouldReturnUnmodifiableMap() {
        Map<String, String> vocab = vocabulary.getVocabulary();
        assertThrows(UnsupportedOperationException.class, () -> {
            vocab.put("test", "test");
        });
    }

    @Test
    void shouldNormalizeAbbreviations() {
        assertEquals("obrigado", vocabulary.normalize("obg"));
        assertEquals("valeu", vocabulary.normalize("vlw"));
        assertEquals("beleza", vocabulary.normalize("blz"));
        assertEquals("tambem", vocabulary.normalize("tb"));
        assertEquals("porque", vocabulary.normalize("pq"));
        assertEquals("que", vocabulary.normalize("q"));
        assertEquals("voce", vocabulary.normalize("vc"));
        assertEquals("voces", vocabulary.normalize("vcs"));
        assertEquals("horas", vocabulary.normalize("hrs"));
        assertEquals("hora", vocabulary.normalize("hr"));
    }

    @Test
    void shouldNormalizeCommonErrors() {
        assertEquals("agendar", vocabulary.normalize("agendr"));
        assertEquals("agendar", vocabulary.normalize("agend"));
        assertEquals("segunda", vocabulary.normalize("segnda"));
        assertEquals("terca", vocabulary.normalize("terca"));
        assertEquals("manicure", vocabulary.normalize("manicuri"));
        assertEquals("unha", vocabulary.normalize("unhas"));
    }

    @Test
    void shouldNormalizeSynonyms() {
        assertEquals("agendar", vocabulary.normalize("marcar"));
        assertEquals("agendar", vocabulary.normalize("agenda"));
        assertEquals("agendar", vocabulary.normalize("reservar"));
        assertEquals("agendamento", vocabulary.normalize("marcacao"));
        assertEquals("agendamento", vocabulary.normalize("consulta"));
        assertEquals("agendamento", vocabulary.normalize("visita"));
    }

    @Test
    void shouldNormalizeEmojis() {
        assertEquals("positivo", vocabulary.normalize("👍"));
        assertEquals("negativo", vocabulary.normalize("👎"));
        assertEquals("obrigado", vocabulary.normalize("🙏"));
        assertEquals("carinho", vocabulary.normalize("❤️"));
        assertEquals("feliz", vocabulary.normalize("😊"));
        assertEquals("triste", vocabulary.normalize("😢"));
        assertEquals("manicure", vocabulary.normalize("💅"));
        assertEquals("cabelo", vocabulary.normalize("💇"));
    }

    @Test
    void shouldNormalizeTextWithMultipleEntries() {
        assertEquals("obrigado voce", vocabulary.normalize("obg vc"));
        assertEquals("quero agendar", vocabulary.normalize("quero marcar"));
        assertEquals("beleza valeu", vocabulary.normalize("blz vlw"));
    }

    @Test
    void shouldHandleNullInput() {
        assertNull(vocabulary.normalize(null));
    }

    @Test
    void shouldHandleBlankInput() {
        assertEquals("", vocabulary.normalize(""));
        assertEquals("   ", vocabulary.normalize("   "));
    }

    @Test
    void shouldPreserveTextWithoutVocabularyEntries() {
        assertEquals("texto sem entrada", vocabulary.normalize("texto sem entrada"));
    }

    @Test
    void shouldNormalizeToLowerCase() {
        assertEquals("obrigado", vocabulary.normalize("OBG"));
        assertEquals("obrigado", vocabulary.normalize("ObG"));
    }

    @Test
    void shouldContainAllRequiredCategories() {
        Map<String, String> vocab = vocabulary.getVocabulary();
        
        // Check for abbreviations
        assertTrue(vocab.containsKey("obg"), "Should contain abbreviation 'obg'");
        assertTrue(vocab.containsKey("vlw"), "Should contain abbreviation 'vlw'");
        
        // Check for common errors
        assertTrue(vocab.containsKey("agendr"), "Should contain error 'agendr'");
        assertTrue(vocab.containsKey("segnda"), "Should contain error 'segnda'");
        
        // Check for synonyms
        assertTrue(vocab.containsKey("marcar"), "Should contain synonym 'marcar'");
        assertTrue(vocab.containsKey("reservar"), "Should contain synonym 'reservar'");
        
        // Check for emojis
        assertTrue(vocab.containsKey("👍"), "Should contain emoji '👍'");
        assertTrue(vocab.containsKey("🙏"), "Should contain emoji '🙏'");
    }

    @Test
    void shouldHaveCorrectMappingValues() {
        Map<String, String> vocab = vocabulary.getVocabulary();
        
        assertEquals("obrigado", vocab.get("obg"));
        assertEquals("valeu", vocab.get("vlw"));
        assertEquals("agendar", vocab.get("marcar"));
        assertEquals("agendar", vocab.get("reservar"));
        assertEquals("positivo", vocab.get("👍"));
    }
}