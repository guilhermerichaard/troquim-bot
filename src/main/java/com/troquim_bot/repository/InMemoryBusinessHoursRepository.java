package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplo em memória do expediente.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}; fora deles a guarda de
 * persistência derruba o startup em vez de deixar o expediente evaporar a cada restart.
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryBusinessHoursRepository implements BusinessHoursRepository {

    private final Map<BusinessId, BusinessHours> armazenamento = new ConcurrentHashMap<>();

    @Override
    public void salvar(BusinessId businessId, BusinessHours expediente) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para salvar expediente");
        }
        if (expediente == null) {
            throw new IllegalArgumentException("Expediente é obrigatório");
        }
        // Substituição, como no adapter real: cadastrar de novo declara a semana, não empilha.
        armazenamento.put(businessId, expediente);
    }

    @Override
    public BusinessHours buscar(BusinessId businessId) {
        if (businessId == null) {
            return BusinessHours.naoConfigurado();
        }
        return armazenamento.getOrDefault(businessId, BusinessHours.naoConfigurado());
    }

    @Override
    public boolean configurado(BusinessId businessId) {
        return businessId != null && !buscar(businessId).naoTemExpediente();
    }
}
