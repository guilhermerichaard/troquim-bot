package com.troquim_bot.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.service.ServiceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do {@link ServiceRepository}, para testes e cenários sem banco.
 *
 * O isolamento por negócio é aplicado AQUI também: um repositório de teste que ignorasse
 * o tenant deixaria passar verde um bug de vazamento que o repositório real pegaria.
 * A chave continua sendo o {@link ServiceId}, mas toda leitura confere o {@link BusinessId}.
 *
 * RESTRITO AOS PERFIS {@code test} e {@code dev-inmemory}. Fora deles este bean nem
 * existe, de modo que produção não tem como cair em catálogo volátil por acidente — a
 * ausência do adapter JPA derruba o startup em vez de degradar em silêncio
 * (ver {@code CatalogoPersistenceGuard}).
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryServiceRepository implements ServiceRepository {

    private final ConcurrentMap<ServiceId, Service> services = new ConcurrentHashMap<>();

    @Override
    public Service salvar(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("Service não pode ser nulo");
        }
        services.put(service.getId(), service);
        return service;
    }

    @Override
    public Optional<Service> buscarPorId(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(services.get(id))
                .filter(service -> service.pertenceAo(businessId));
    }

    @Override
    public List<Service> listarAtivos(BusinessId businessId) {
        List<Service> encontrados = new ArrayList<>();
        if (businessId == null) {
            return encontrados;
        }
        for (Service service : services.values()) {
            if (service.pertenceAo(businessId) && service.getStatus() == ServiceStatus.ATIVO) {
                encontrados.add(service);
            }
        }
        return encontrados;
    }

    @Override
    public List<Service> listarTodos(BusinessId businessId) {
        List<Service> encontrados = new ArrayList<>();
        if (businessId == null) {
            return encontrados;
        }
        for (Service service : services.values()) {
            if (service.pertenceAo(businessId)) {
                encontrados.add(service);
            }
        }
        return encontrados;
    }

    @Override
    public void remover(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            return;
        }
        // Remoção também é escopada: não se apaga dado de outro negócio por id adivinhado.
        services.computeIfPresent(id,
                (chave, service) -> service.pertenceAo(businessId) ? null : service);
    }
}
