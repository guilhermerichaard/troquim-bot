package com.troquim_bot.application.language.vocabulary;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Brazilian vocabulary seed for the Language Layer.
 * Organized by categories to support natural language understanding.
 *
 * Categories:
 * - ABREVIACOES: Common Brazilian abbreviations
 * - ERROS_COMUNS: Common typing errors and misspellings
 * - SINONIMOS: Synonyms for key terms
 * - EMOJIS: Emoji meanings
 */
public class BrazilianVocabulary {

    private final Map<String, String> vocabulary;

    public BrazilianVocabulary() {
        this.vocabulary = new HashMap<>();
        initializeVocabulary();
    }

    /**
     * Returns an unmodifiable view of the vocabulary map.
     * Key: informal/incorrect form
     * Value: normalized/correct form
     */
    public Map<String, String> getVocabulary() {
        return Collections.unmodifiableMap(vocabulary);
    }

    /**
     * Normalizes a text by replacing vocabulary entries with their normalized forms.
     * Uses word boundaries to avoid partial matches within words.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String normalized = text.toLowerCase();
        for (Map.Entry<String, String> entry : vocabulary.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // Use word boundaries to avoid replacing within words
            // Special handling for emojis and special characters
            if (key.matches("^[\\p{L}\\p{N}]+$")) {
                // For alphanumeric keys, use word boundaries
                normalized = normalized.replaceAll("\\b" + Pattern.quote(key) + "\\b", value);
            } else {
                // For emojis and special characters, use simple replacement
                normalized = normalized.replace(key, value);
            }
        }
        return normalized;
    }

    private void initializeVocabulary() {
        // ABREVIACOES - Common Brazilian abbreviations
        addEntry("obg", "obrigado");
        addEntry("vlw", "valeu");
        addEntry("blz", "beleza");
        addEntry("tb", "tambem");
        addEntry("pq", "porque");
        addEntry("q", "que");
        addEntry("vc", "voce");
        addEntry("vcs", "voces");
        addEntry("hrs", "horas");
        addEntry("hr", "hora");
        addEntry("msg", "mensagem");
        addEntry("td", "tudo");
        addEntry("tbm", "tambem");
        addEntry("n", "nao");
        addEntry("s", "sim");
        addEntry("pqp", "porque");
        addEntry("mto", "muito");
        addEntry("mt", "muito");

        // ERROS_COMUNS - Common typing errors and misspellings
        addEntry("agendr", "agendar");
        addEntry("agend", "agendar");
        addEntry("segnda", "segunda");
        addEntry("terca", "terca");
        addEntry("terc", "terca");
        addEntry("quarta", "quarta");
        addEntry("quinta", "quinta");
        addEntry("sexta", "sexta");
        addEntry("sabado", "sabado");
        addEntry("domingo", "domingo");
        addEntry("manicuri", "manicure");
        addEntry("unhas", "unha");
        addEntry("cabelo", "cabelo");
        addEntry("maquiagem", "maquiagem");
        addEntry("servico", "servico");
        addEntry("servicos", "servicos");
        addEntry("preco", "preco");
        addEntry("valor", "valor");
        addEntry("horario", "horario");
        addEntry("horarios", "horarios");
        addEntry("endereco", "endereco");
        addEntry("localizacao", "localizacao");

        // SINONIMOS - Synonyms for key terms
        addEntry("marcar", "agendar");
        addEntry("agenda", "agendar");
        addEntry("reservar", "agendar");
        addEntry("marcacao", "agendamento");
        addEntry("agendamento", "agendamento");
        addEntry("consulta", "agendamento");
        addEntry("visita", "agendamento");
        addEntry("atendimento", "agendamento");
        addEntry("corte", "corte");
        addEntry("penteado", "penteado");
        addEntry("mao", "mao");
        addEntry("pes", "pes");
        addEntry("rosto", "rosto");
        addEntry("corpo", "corpo");

        // EMOJIS - Emoji meanings
        addEntry("👍", "positivo");
        addEntry("👎", "negativo");
        addEntry("🙏", "obrigado");
        addEntry("❤️", "carinho");
        addEntry("😊", "feliz");
        addEntry("😢", "triste");
        addEntry("😡", "bravo");
        addEntry("🎉", "celebracao");
        addEntry("💅", "manicure");
        addEntry("💇", "cabelo");
        addEntry("💆", "massagem");
        addEntry("✨", "brilho");
        addEntry("🙌", "animado");
        addEntry("👏", "parabens");
        addEntry("💯", "perfeito");
    }

    private void addEntry(String key, String value) {
        vocabulary.put(key, value);
    }
}