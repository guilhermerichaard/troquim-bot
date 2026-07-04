package com.troquim_bot.application.language;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BusinessDictionary {

    private static final int MAX_ENTRIES = 30;

    private final Map<String, String> entries;

    public BusinessDictionary() {
        this(defaultEntries());
    }

    public BusinessDictionary(Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Business dictionary must have at most " + MAX_ENTRIES + " entries");
        }

        this.entries = Map.copyOf(entries);
    }

    public String canonicalize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return "";
        }

        String[] tokens = normalizedText.split("\\s+");
        StringBuilder canonical = new StringBuilder();

        for (String token : tokens) {
            String replacement = entries.getOrDefault(token, token);
            if (!canonical.isEmpty()) {
                canonical.append(' ');
            }
            canonical.append(replacement);
        }

        return canonical.toString().trim().replaceAll("\\s+", " ");
    }

    public Optional<String> lookup(String token) {
        if (token == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(entries.get(token));
    }

    public Map<String, String> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    private static Map<String, String> defaultEntries() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("vc", "voce");
        defaults.put("vcs", "voces");
        defaults.put("tb", "tambem");
        defaults.put("tbm", "tambem");
        defaults.put("td", "tudo");
        defaults.put("pq", "porque");
        defaults.put("q", "que");
        defaults.put("hj", "hoje");
        defaults.put("msg", "mensagem");
        defaults.put("zap", "whatsapp");
        defaults.put("whats", "whatsapp");
        defaults.put("hr", "hora");
        defaults.put("hrs", "horas");
        defaults.put("min", "minuto");
        defaults.put("mins", "minutos");
        defaults.put("blz", "beleza");
        defaults.put("obg", "obrigado");
        defaults.put("pfv", "por favor");
        defaults.put("ag", "agendar");
        defaults.put("sobr", "sobrancelha");
        defaults.put("agendr", "agendar");
        defaults.put("agend", "agendar");
        defaults.put("segnda", "segunda");
        defaults.put("manicuri", "manicure");
        defaults.put("unhas", "unha");
        defaults.put("cabelo", "cabelo");
        defaults.put("maquiag", "maquiagem");
        defaults.put("estetic", "estetica");
        defaults.put("depil", "depilacao");
        defaults.put("massag", "massagem");
        return defaults;
    }
}
