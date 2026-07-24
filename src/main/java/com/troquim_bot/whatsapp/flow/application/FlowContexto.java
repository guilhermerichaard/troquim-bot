package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.catalog.FlowProfessionalOption;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowServiceOption;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Escolhas já validadas do cliente ao longo do Flow.
 *
 * O Flow é STATELESS no servidor: cada resposta devolve as escolhas acumuladas e a tela
 * seguinte as reenvia. Isso evita uma máquina de estados paralela à conversa, mas obriga
 * a REVALIDAR tudo a cada passo — este contexto só é construído a partir de valores que
 * já passaram por {@link FlowDataParser}, nunca copiados cruamente do payload.
 *
 * Campos ao fim da cadeia são nulos enquanto o cliente não chegou naquela tela.
 */
public record FlowContexto(FlowServiceOption servico,
                           FlowProfessionalOption profissional,
                           LocalDate data,
                           LocalTime horario,
                           String nome,
                           String observacao) {

    public static FlowContexto de(FlowServiceOption servico) {
        return new FlowContexto(servico, null, null, null, null, null);
    }

    public FlowContexto com(FlowProfessionalOption profissional) {
        return new FlowContexto(servico, profissional, data, horario, nome, observacao);
    }

    public FlowContexto com(LocalDate data) {
        return new FlowContexto(servico, profissional, data, horario, nome, observacao);
    }

    public FlowContexto com(LocalTime horario) {
        return new FlowContexto(servico, profissional, data, horario, nome, observacao);
    }

    public FlowContexto comDadosPessoais(String nome, String observacao) {
        return new FlowContexto(servico, profissional, data, horario, nome, observacao);
    }
}
