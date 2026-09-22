package com.troquim_bot.availability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Política pura de ranking de slots.
 *
 * A disponibilidade já chega validada. Esta política NÃO cria horários e NÃO ignora
 * preferência do cliente: ela só ordena candidatos válidos para tornar a agenda mais
 * compacta sem sacrificar a intenção expressa.
 */
public final class SlotRecommendationPolicy {

    public record Candidate(LocalDate date, LocalTime time, int adjacencyGapMinutes) {
        public Candidate {
            if (date == null || time == null) {
                throw new IllegalArgumentException("Data e horário são obrigatórios");
            }
            adjacencyGapMinutes = Math.max(0, adjacencyGapMinutes);
        }
    }

    public List<Candidate> rank(List<Candidate> candidates,
                                LocalTime targetTime,
                                int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        Comparator<Candidate> comparator = Comparator.comparing(Candidate::date);

        if (targetTime != null) {
            comparator = comparator
                    .thenComparingLong(c -> distanceMinutes(c.time(), targetTime))
                    .thenComparingInt(Candidate::adjacencyGapMinutes)
                    .thenComparing(Candidate::time);
        } else {
            comparator = comparator
                    .thenComparingInt(Candidate::adjacencyGapMinutes)
                    .thenComparing(Candidate::time);
        }

        return candidates.stream()
                .sorted(comparator)
                .limit(limit)
                .toList();
    }

    private static long distanceMinutes(LocalTime a, LocalTime b) {
        return Math.abs(ChronoUnit.MINUTES.between(a, b));
    }
}
