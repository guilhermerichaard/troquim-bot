package com.troquim_bot.conversation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretador determinístico inicial do Turbo Booking.
 *
 * Ele existe atrás de uma porta para que um modelo de IA possa substituí-lo depois sem
 * deslocar nenhuma regra de negócio para fora do Domain.
 */
@Component
public class HeuristicBookingIntentInterpreter implements BookingIntentInterpreter {

    private static final Pattern ENTRE = Pattern.compile(
            "\\bentre\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?)?\\s+(?:e|ate)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?)?\\b");
    private static final Pattern DEPOIS = Pattern.compile(
            "\\b(?:depois|apos|a partir)\\s+(?:das?|de)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?)?\\b");
    private static final Pattern ANTES = Pattern.compile(
            "\\bantes\\s+(?:das?|de)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?)?\\b");
    private static final Pattern EXATO = Pattern.compile(
            "\\b(?:as|para|por volta das?)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?)?\\b|\\b(\\d{1,2})h(?:(\\d{2}))?\\b");

    @Override
    public Optional<BookingIntent> interpretar(String mensagem) {
        String texto = normalizar(mensagem);
        if (texto.isBlank()) {
            return Optional.empty();
        }

        boolean mesmoDeSempre = texto.contains("mesmo de sempre")
                || texto.contains("o de sempre")
                || texto.equals("o mesmo")
                || texto.contains("mesmo servico")
                || texto.contains("mesmo serviço");

        String dia = extrairDia(texto);
        TimePreference tempo = extrairHorario(texto);

        boolean sinalDeAgendamento = mesmoDeSempre
                || texto.contains("agendar")
                || texto.contains("marcar")
                || texto.contains("horario")
                || texto.contains("horário")
                || texto.contains("quero fazer")
                || texto.contains("preciso fazer")
                || !dia.isBlank()
                || tempo.temPreferencia();

        if (!sinalDeAgendamento) {
            return Optional.empty();
        }

        return Optional.of(new BookingIntent(
                mensagem == null ? "" : mensagem,
                dia,
                tempo.inicio(),
                tempo.fim(),
                tempo.alvo(),
                mesmoDeSempre));
    }

    private static String extrairDia(String texto) {
        if (texto.contains("amanha")) return "amanha";
        if (texto.contains("hoje")) return "hoje";
        if (texto.contains("segunda")) return "segunda";
        if (texto.contains("terca")) return "terca";
        if (texto.contains("quarta")) return "quarta";
        if (texto.contains("quinta")) return "quinta";
        if (texto.contains("sexta")) return "sexta";
        if (texto.contains("sabado")) return "sabado";
        if (texto.contains("domingo")) return "domingo";
        return "";
    }

    private static TimePreference extrairHorario(String texto) {
        if (texto.contains("de manha") || texto.contains("pela manha")) {
            return new TimePreference(LocalTime.of(8, 0), LocalTime.of(11, 59), LocalTime.of(10, 0));
        }
        if (texto.contains("a tarde") || texto.contains("de tarde") || texto.contains("pela tarde")) {
            return new TimePreference(LocalTime.of(12, 0), LocalTime.of(17, 59), LocalTime.of(15, 0));
        }
        if (texto.contains("a noite") || texto.contains("de noite") || texto.contains("pela noite")) {
            return new TimePreference(LocalTime.of(18, 0), LocalTime.of(23, 59), LocalTime.of(19, 0));
        }

        Matcher entre = ENTRE.matcher(texto);
        if (entre.find()) {
            LocalTime inicio = time(entre.group(1), entre.group(2));
            LocalTime fim = time(entre.group(3), entre.group(4));
            if (inicio != null && fim != null && !fim.isBefore(inicio)) {
                return new TimePreference(inicio, fim, midpoint(inicio, fim));
            }
        }

        Matcher depois = DEPOIS.matcher(texto);
        if (depois.find()) {
            LocalTime inicio = time(depois.group(1), depois.group(2));
            return inicio == null
                    ? TimePreference.none()
                    : new TimePreference(inicio, null, inicio);
        }

        Matcher antes = ANTES.matcher(texto);
        if (antes.find()) {
            LocalTime fim = time(antes.group(1), antes.group(2));
            return fim == null
                    ? TimePreference.none()
                    : new TimePreference(null, fim, fim);
        }

        Matcher exato = EXATO.matcher(texto);
        if (exato.find()) {
            String h = exato.group(1) != null ? exato.group(1) : exato.group(3);
            String m = exato.group(1) != null ? exato.group(2) : exato.group(4);
            LocalTime alvo = time(h, m);
            return alvo == null
                    ? TimePreference.none()
                    : new TimePreference(alvo, alvo, alvo);
        }

        return TimePreference.none();
    }

    private static LocalTime time(String hora, String minuto) {
        try {
            int h = Integer.parseInt(hora);
            int m = minuto == null ? 0 : Integer.parseInt(minuto);
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return null;
            }
            return LocalTime.of(h, m);
        } catch (RuntimeException invalido) {
            return null;
        }
    }

    private static LocalTime midpoint(LocalTime inicio, LocalTime fim) {
        long a = inicio.toSecondOfDay();
        long b = fim.toSecondOfDay();
        return LocalTime.ofSecondOfDay(a + ((b - a) / 2));
    }

    private static String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record TimePreference(LocalTime inicio, LocalTime fim, LocalTime alvo) {
        static TimePreference none() {
            return new TimePreference(null, null, null);
        }

        boolean temPreferencia() {
            return inicio != null || fim != null || alvo != null;
        }
    }
}
