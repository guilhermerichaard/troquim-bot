package com.troquim_bot.availability;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Única porta de entrada do "agora" nas regras de agenda.
 *
 * POR QUE ISTO EXISTE: {@code LocalDate.now()} espalhado pela regra torna o comportamento
 * dependente do relógio da máquina que roda o teste. O sintoma clássico é a suíte que passa
 * de manhã e falha às 23h50, ou o teste de "horário passado não aparece" que só funciona
 * depois do meio-dia. Com o relógio injetado, o teste ESCOLHE o instante e a regra fica
 * determinística.
 *
 * ZONA: no MVP preserva-se a zona do ambiente ({@link Clock#systemDefaultZone()}), a mesma
 * que o sistema já usava. Não se assume UTC — assumir UTC mudaria silenciosamente a fronteira
 * do dia para um salão brasileiro. Fuso por negócio está fora desta etapa, mas isolar a
 * resolução aqui é justamente o que permitirá introduzi-lo depois sem caçar {@code now()}
 * espalhado pelo código.
 */
@Component
public class RelogioDoNegocio {

    private final Clock clock;

    public RelogioDoNegocio() {
        this(Clock.systemDefaultZone());
    }

    public RelogioDoNegocio(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock é obrigatório");
        }
        this.clock = clock;
    }

    /** Relógio fixo, para testes que precisam de um instante determinístico. */
    public static RelogioDoNegocio fixo(LocalDateTime instante) {
        Clock base = Clock.systemDefaultZone();
        return new RelogioDoNegocio(
                Clock.fixed(instante.atZone(base.getZone()).toInstant(), base.getZone()));
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public LocalTime agora() {
        return LocalTime.now(clock);
    }

    public LocalDateTime instante() {
        return LocalDateTime.now(clock);
    }
}
