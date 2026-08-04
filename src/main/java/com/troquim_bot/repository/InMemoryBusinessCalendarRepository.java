package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessId;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplo em memória do calendário do negócio.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}; fora deles a guarda de
 * persistência derruba o startup em vez de deixar o expediente evaporar a cada restart.
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryBusinessCalendarRepository implements BusinessCalendarRepository {

    private final Map<BusinessId, BusinessCalendar> armazenamento = new ConcurrentHashMap<>();

    @Override
    public void salvar(BusinessCalendar calendario) {
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário é obrigatório");
        }
        // Substituição, como no adapter real: cadastrar de novo declara a semana, não empilha.
        armazenamento.put(calendario.getBusinessId(), calendario);
    }

    @Override
    public BusinessCalendar buscar(BusinessId businessId) {
        if (businessId == null) {
            return null;
        }
        return armazenamento.getOrDefault(businessId, BusinessCalendar.naoConfigurado(businessId));
    }

    @Override
    public boolean configurado(BusinessId businessId) {
        return businessId != null && !buscar(businessId).naoConfigurado();
    }
}
