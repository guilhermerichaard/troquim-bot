package com.troquim_bot.controller.dto;

import com.troquim_bot.application.availability.ConsultarDisponibilidade;

import java.time.LocalTime;
import java.util.List;

/** Disponibilidade pública de um dia — condição estável + horários livres. */
public class PublicAvailabilityResponse {

    private String date;
    private String condition;
    private List<String> times;

    public PublicAvailabilityResponse() {
    }

    public PublicAvailabilityResponse(String date, String condition, List<String> times) {
        this.date = date;
        this.condition = condition;
        this.times = times;
    }

    public static PublicAvailabilityResponse from(ConsultarDisponibilidade.AgendaDoDia agenda) {
        List<String> horarios = agenda.horarios().stream().map(LocalTime::toString).toList();
        return new PublicAvailabilityResponse(
                agenda.data().toString(),
                PublicAvailabilityCondition.de(agenda.condicao()),
                horarios);
    }

    public String getDate() {
        return date;
    }

    public String getCondition() {
        return condition;
    }

    public List<String> getTimes() {
        return times;
    }
}
