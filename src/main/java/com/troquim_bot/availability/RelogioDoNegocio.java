package com.troquim_bot.availability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Única porta de entrada do "agora" nas regras de agenda.
 *
 * POR QUE ISTO EXISTE: {@code LocalDate.now()} espalhado pela regra torna o comportamento
 * dependente do relógio da máquina que roda o teste. O sintoma clássico é a suíte que passa
 * de manhã e falha às 23h50, ou o teste de "horário passado não aparece" que só funciona
 * depois do meio-dia. Com o relógio injetado, o teste ESCOLHE o instante e a regra fica
 * determinística.
 *
 * ZONA: em runtime Spring a zona é explícita em {@code troquim.business.time-zone};
 * o piloto usa America/Sao_Paulo. O construtor sem argumentos existe apenas para
 * compatibilidade de objetos criados manualmente fora do container. Fuso persistido por
 * Business continua sendo a evolução arquitetural planejada.
 */
@Component
public class RelogioDoNegocio {

    private final Clock clock;

    public RelogioDoNegocio() {
        this(Clock.systemDefaultZone());
    }

    @Autowired
    public RelogioDoNegocio(@Value("${troquim.business.time-zone}") String zoneId) {
        this(Clock.system(ZoneId.of(zoneId)));
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
