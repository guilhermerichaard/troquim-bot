package com.troquim_bot.application.conversation.engine;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultEntityExtractor implements EntityExtractor {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*h?\\b");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:meu nome e|me chamo|sou o|sou a|sou)\\s+([a-zA-ZÀ-ÿ]{2,})");

    @Override
    public ExtractedEntities extract(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return ExtractedEntities.empty();
        }

        String service = extractService(normalized);
        String day = extractDay(normalized);
        String time = extractTime(normalized);
        String customerName = extractName(normalized);

        return new ExtractedEntities(service, day, time, customerName);
    }

    private String extractService(String text) {
        if (containsAny(text, "unha", "manicure", "pedicure")) {
            return "unha";
        }
        if (containsAny(text, "sobrancelha", "design")) {
            return "sobrancelha";
        }
        if (containsAny(text, "cabelo", "corte", "escova", "progressiva")) {
            return "cabelo";
        }
        return null;
    }

    private String extractDay(String text) {
        String[] days = {"segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo", "hoje", "amanha"};
        for (String day : days) {
            if (hasTerm(text, day)) {
                return day;
            }
        }
        return null;
    }

    private String extractTime(String text) {
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        int hour = Integer.parseInt(matcher.group(1));
        String minute = matcher.group(2) == null ? "00" : matcher.group(2);
        return String.format(Locale.ROOT, "%02d:%s", hour, minute);
    }

    private String extractName(String text) {
        Matcher matcher = NAME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return capitalize(matcher.group(1));
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (hasTerm(text, term)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTerm(String text, String term) {
        return (" " + text + " ").contains(" " + term + " ");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(message, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        return withoutAccents
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9:]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }
}
