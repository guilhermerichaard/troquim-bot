package com.troquim_bot.business;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Value Object que representa o horário de funcionamento do negócio.
 * Imutável e validado na criação.
 */
public class BusinessHours {

    private final LocalTime abertura;
    private final LocalTime fechamento;
    private final Set<DiaSemana> diasFuncionamento;

    public BusinessHours(LocalTime abertura, LocalTime fechamento, Set<DiaSemana> diasFuncionamento) {
        if (abertura == null) {
            throw new IllegalArgumentException("Horário de abertura é obrigatório");
        }
        if (fechamento == null) {
            throw new IllegalArgumentException("Horário de fechamento é obrigatório");
        }
        if (diasFuncionamento == null || diasFuncionamento.isEmpty()) {
            throw new IllegalArgumentException("Deve ter pelo menos um dia de funcionamento");
        }
        if (abertura.isAfter(fechamento) || abertura.equals(fechamento)) {
            throw new IllegalArgumentException("Horário de abertura deve ser anterior ao fechamento");
        }

        this.abertura = abertura;
        this.fechamento = fechamento;
        this.diasFuncionamento = new HashSet<>(diasFuncionamento);
    }

    public LocalTime getAbertura() {
        return abertura;
    }

    public LocalTime getFechamento() {
        return fechamento;
    }

    public Set<DiaSemana> getDiasFuncionamento() {
        return new HashSet<>(diasFuncionamento);
    }

    public boolean isDiaFuncionamento(DiaSemana dia) {
        return diasFuncionamento.contains(dia);
    }

    public boolean estaAberto(LocalTime horario) {
        return !horario.isBefore(abertura) && !horario.isAfter(fechamento);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessHours that = (BusinessHours) o;
        return abertura.equals(that.abertura) && fechamento.equals(that.fechamento) && diasFuncionamento.equals(that.diasFuncionamento);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{abertura, fechamento, diasFuncionamento});
    }
}