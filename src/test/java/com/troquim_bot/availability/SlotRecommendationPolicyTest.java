package com.troquim_bot.availability;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotRecommendationPolicyTest {

    @Test
    void prefereProximidadeDoHorarioPedidoAntesDeCompactacao() {
        SlotRecommendationPolicy policy = new SlotRecommendationPolicy();
        LocalDate data = LocalDate.of(2026, 9, 25);

        var ranked = policy.rank(List.of(
                new SlotRecommendationPolicy.Candidate(data, LocalTime.of(16, 0), 0),
                new SlotRecommendationPolicy.Candidate(data, LocalTime.of(17, 0), 45),
                new SlotRecommendationPolicy.Candidate(data, LocalTime.of(17, 30), 0)),
                LocalTime.of(17, 0),
                3);

        assertEquals(LocalTime.of(17, 0), ranked.get(0).time());
        assertEquals(LocalTime.of(17, 30), ranked.get(1).time());
        assertEquals(LocalTime.of(16, 0), ranked.get(2).time());
    }

    @Test
    void semHorarioAlvoPrefereSlotQueCompactaAgenda() {
        SlotRecommendationPolicy policy = new SlotRecommendationPolicy();
        LocalDate data = LocalDate.of(2026, 9, 25);

        var ranked = policy.rank(List.of(
                new SlotRecommendationPolicy.Candidate(data, LocalTime.of(10, 0), 60),
                new SlotRecommendationPolicy.Candidate(data, LocalTime.of(14, 0), 0)),
                null,
                2);

        assertEquals(LocalTime.of(14, 0), ranked.get(0).time());
    }
}
