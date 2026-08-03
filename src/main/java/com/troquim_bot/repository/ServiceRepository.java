package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;

import java.util.List;
import java.util.Optional;

/**
 * Port de persistência do catálogo de serviços.
 *
 * TENANT OBRIGATÓRIO EM TODA CONSULTA. Deliberadamente não existe `findById(ServiceId)`
 * nem `findAll()`: catálogo é sempre por negócio, e uma assinatura sem {@link BusinessId}
 * permitiria vazamento entre clientes sem que ninguém percebesse. O tenant é argumento
 * explícito — nunca contexto implícito, ThreadLocal ou dedução da Infrastructure.
 *
 * Interface pura, sem dependência de frameworks. A implementação vive na Infrastructure.
 */
public interface ServiceRepository {

    Service salvar(Service service);

    /**
     * Vazio quando o id não existe OU pertence a outro negócio — os dois casos são
     * indistinguíveis de fora, de propósito: não revelar a existência de dado alheio.
     */
    Optional<Service> buscarPorId(BusinessId businessId, ServiceId id);

    /** Somente serviços ATIVOS — é o que o catálogo deve oferecer ao cliente. */
    List<Service> listarAtivos(BusinessId businessId);

    /** Inclui inativos; para administração, não para oferta ao cliente. */
    List<Service> listarTodos(BusinessId businessId);

    void remover(BusinessId businessId, ServiceId id);
}
