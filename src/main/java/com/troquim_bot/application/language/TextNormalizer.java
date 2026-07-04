package com.troquim_bot.application.language;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern REPEATED_LETTERS = Pattern.compile("([a-z])\\1{2,}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
        Map.entry("obg", "obrigado"),
        Map.entry("vlw", "valeu"),
        Map.entry("hrs", "horas"),
        Map.entry("hr", "hora")
    );

    public String normalize(String text) {
        if (text == null) {
            return "";
        }

        String withoutAccents = removeAccents(text);
        String lowercase = withoutAccents.toLowerCase(Locale.ROOT);
        String withoutRepeatedLetters = collapseRepeatedLetters(lowercase);
        String wordsOnly = NON_ALPHANUMERIC.matcher(withoutRepeatedLetters).replaceAll(" ");
        String normalizedAbbreviations = normalizeAbbreviations(wordsOnly);

        return SPACES.matcher(normalizedAbbreviations).replaceAll(" ").trim();
    }

    public String removeAccents(String text) {
        if (text == null) {
            return "";
        }

        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    public String collapseRepeatedLetters(String text) {
        if (text == null) {
            return "";
        }

        return REPEATED_LETTERS.matcher(text).replaceAll("$1");
    }

    public String normalizeAbbreviations(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;
        for (Map.Entry<String, String> entry : ABBREVIATIONS.entrySet()) {
            String abbreviation = entry.getKey();
            String expansion = entry.getValue();
            result = result.replaceAll("\\b" + abbreviation + "\\b", expansion);
        }

        return result;
    }
}
