package com.troquim_bot.conversation;

import com.troquim_bot.availability.RelogioDoNegocio;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Política única de saudação por horário local do negócio.
 *
 * Não tenta inferir pelo texto do cliente: "boa noite" enviado às 10h continua
 * recebendo "Bom dia". A fonte temporal é a mesma usada pela agenda.
 */
@Component
public class SaudacaoDoNegocio {

    private final RelogioDoNegocio relogio;

    public SaudacaoDoNegocio(RelogioDoNegocio relogio) {
        if (relogio == null) {
            throw new IllegalArgumentException("Relógio do negócio é obrigatório");
        }
        this.relogio = relogio;
    }

    public String atual() {
        LocalTime agora = relogio.agora();
        if (!agora.isBefore(LocalTime.of(5, 0)) && agora.isBefore(LocalTime.NOON)) {
            return "Bom dia";
        }
        if (!agora.isBefore(LocalTime.NOON) && agora.isBefore(LocalTime.of(18, 0))) {
            return "Boa tarde";
        }
        return "Boa noite";
    }
}
