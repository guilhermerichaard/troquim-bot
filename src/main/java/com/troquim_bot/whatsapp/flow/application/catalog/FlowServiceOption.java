package com.troquim_bot.whatsapp.flow.application.catalog;

import com.troquim_bot.service.ServiceId;

import java.time.Duration;

/**
 * Serviço oferecido na tela SERVICO.
 *
 * IDENTIDADE REAL: a opção carrega o {@link ServiceId} do catálogo persistido, não uma
 * chave textual. O id enviado à tela é o UUID desse ServiceId em texto — sem hash, sem
 * slug, sem nome e sem mapa paralelo. O mesmo UUID volta do Flow, é reparseado em
 * {@link ServiceId} e chega ao {@code appointment.service_id} idêntico ao do catálogo.
 *
 * O preço vive no catálogo como {@code Optional} e não é exposto aqui: a tela do MVP não
 * mostra preço, e inventar um valor seria criar informação que o negócio não declarou.
 */
public record FlowServiceOption(ServiceId servicoId, String titulo, Duration duracao) {

    public FlowServiceOption {
        if (servicoId == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório numa opção de serviço do Flow");
        }
    }

    /**
     * Id textual trafegado nas telas: o UUID do serviço, tal e qual.
     *
     * Deliberadamente NÃO é uma chave derivada. Qualquer transformação aqui quebraria a
     * identidade ponta a ponta e faria o agendamento apontar para um serviço que não existe.
     */
    public String id() {
        return servicoId.getValue().toString();
    }

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
