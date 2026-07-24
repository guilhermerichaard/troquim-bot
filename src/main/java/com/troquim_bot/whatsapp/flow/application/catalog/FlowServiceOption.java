package com.troquim_bot.whatsapp.flow.application.catalog;

import java.time.Duration;

/**
 * Serviço oferecido na tela SERVICO.
 *
 * O {@code id} é a chave canônica do serviço no MVP (ex.: {@code "unha"}) — a mesma
 * string que o {@code BookingApplicationService} usa para derivar o {@code ServiceId}
 * determinístico. Manter uma única chave evita que o Flow crie um segundo vocabulário
 * de serviços paralelo ao do menu de conversa.
 *
 * Não há preço: o domínio ainda não modela preço de serviço. Inventar um valor na tela
 * seria criar informação que o sistema não tem.
 */
public record FlowServiceOption(String id, String titulo, Duration duracao) {

    /** Rótulo de duração para exibição, ex.: "1h" ou "30min". */
    public String duracaoLegivel() {
        long horas = duracao.toHours();
        long minutos = duracao.toMinutesPart();
        if (horas > 0 && minutos > 0) {
            return horas + "h" + minutos;
        }
        return horas > 0 ? horas + "h" : minutos + "min";
    }
}
