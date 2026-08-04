package com.troquim_bot.business;

import java.time.DayOfWeek;
import java.time.LocalDate;

public enum DiaSemana {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO,
    DOMINGO;

    /**
     * Dia da semana de uma data.
     *
     * Ponto ÚNICO dessa conversão. Antes ela existia como {@code switch} sobre nomes em
     * português espalhado pelo código — um deles com acento, outro sem, e a divergência
     * fazia um dia inteiro simplesmente não ter horários.
     */
    public static DiaSemana de(LocalDate data) {
        if (data == null) {
            throw new IllegalArgumentException("Data é obrigatória para resolver o dia da semana");
        }
        return de(data.getDayOfWeek());
    }

    public static DiaSemana de(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> SEGUNDA;
            case TUESDAY -> TERCA;
            case WEDNESDAY -> QUARTA;
            case THURSDAY -> QUINTA;
            case FRIDAY -> SEXTA;
            case SATURDAY -> SABADO;
            case SUNDAY -> DOMINGO;
        };
    }
}
