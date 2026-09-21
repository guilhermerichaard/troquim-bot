package com.troquim_bot.application.language;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;

import java.util.Optional;

/**
 * Memoria confirmada da camada de interpretacao.
 *
 * Nao decide disponibilidade nem cria servicos. Apenas recorda que, para um tenant,
 * uma entrada NORMALIZADA ja foi explicitamente confirmada pelo cliente como referencia
 * a um ServiceId existente. O catalogo canonico continua validando se esse servico ainda
 * pode ser ofertado.
 */
public interface ServiceInterpretationLearningStore {

    Optional<ServiceId> buscar(BusinessId businessId, String entradaNormalizada);

    void aprender(BusinessId businessId, String entradaNormalizada, ServiceId serviceId);
}
