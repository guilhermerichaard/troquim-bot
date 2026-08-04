package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessId;

/**
 * Port de persistência do CALENDÁRIO do negócio — a única autoridade sobre expediente.
 *
 * O expediente é gravado e lido INTEIRO, nunca linha a linha: substituir o calendário é uma
 * operação só, e não uma sequência de inserts que possa parar no meio deixando segunda
 * cadastrada e sábado não.
 *
 * TENANT OBRIGATÓRIO em toda operação. Continua persistindo fisicamente em business_hours —
 * trocar a porta de {@code BusinessHoursRepository} para esta não move dado nenhum.
 */
public interface BusinessCalendarRepository {

    /**
     * Substitui o calendário do negócio pelo informado.
     *
     * Substituição, não acréscimo: salvar de novo é declarar como a semana é agora, não
     * empilhar períodos sobre os antigos.
     */
    void salvar(BusinessCalendar calendario);

    /**
     * Calendário do negócio. Nunca {@code null}: negócio sem calendário devolve
     * {@link BusinessCalendar#naoConfigurado(BusinessId)}, estado que o chamador é obrigado a
     * tratar.
     */
    BusinessCalendar buscar(BusinessId businessId);

    /** O negócio já publicou algum período? Distingue "fechado hoje" de "nunca configurado". */
    boolean configurado(BusinessId businessId);
}
