package com.troquim_bot.conversation;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeInputParser {

    private static final Pattern HORARIO_PATTERN = Pattern.compile(
            "(?iu)(?:\\b(?:as)\\s*)?(\\d{1,2})(?::(\\d{2}))?(?:\\s*(?:h(?:oras?)?)\\b)?"
    );

    public Optional<LocalTime> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String texto = normalizar(input);
        Matcher matcher = HORARIO_PATTERN.matcher(texto);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String horaStr = matcher.group(1);
        String minutoStr = matcher.group(2);

        if (horaStr == null) {
            return Optional.empty();
        }

        try {
            int hora = Integer.parseInt(horaStr);
            int minuto = (minutoStr != null && !minutoStr.isBlank()) ? Integer.parseInt(minutoStr) : 0;

            if (hora < 0 || hora > 23) {
                return Optional.empty();
            }
            if (minuto < 0 || minuto > 59) {
                return Optional.empty();
            }

            return Optional.of(LocalTime.of(hora, minuto));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }
}
