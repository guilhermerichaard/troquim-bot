package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;

/**
 * Port de persistência do EXPEDIENTE do negócio.
 *
 * O expediente é um Value Object ({@link BusinessHours}) e não um agregado próprio: ele não
 * tem identidade nem ciclo de vida independentes do negócio. Por isso a porta grava e lê o
 * expediente INTEIRO de um tenant, em vez de expor linhas soltas — substituir o expediente
 * é uma operação só, e não uma sequência de inserts que possa parar no meio deixando
 * segunda cadastrada e sábado não.
 *
 * TENANT OBRIGATÓRIO em toda operação.
 */
public interface BusinessHoursRepository {

    /**
     * Substitui o expediente do negócio pelo informado.
     *
     * Substituição, não acréscimo: cadastrar o expediente de novo é declarar como a semana
     * é agora, não empilhar períodos sobre os antigos.
     */
    void salvar(BusinessId businessId, BusinessHours expediente);

    /**
     * Expediente do negócio. Nunca {@code null}: negócio sem expediente devolve
     * {@link BusinessHours#naoConfigurado()}, estado que o chamador é obrigado a tratar.
     */
    BusinessHours buscar(BusinessId businessId);

    /** O negócio já publicou algum período? Distingue "fechado hoje" de "nunca configurado". */
    boolean configurado(BusinessId businessId);
}
