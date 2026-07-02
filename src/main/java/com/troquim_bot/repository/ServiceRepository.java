package com.troquim_bot.repository;

import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;

import java.util.List;

/**
 * Repository abstraction para persistência de Service.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface ServiceRepository {

    /**
     * Salva um Service (cria ou atualiza).
     */
    Service save(Service service);

    /**
     * Busca um Service por ID.
     * 
     * @return Service se encontrado, null caso contrário
     */
    Service findById(ServiceId id);

    /**
     * Verifica se existe um Service com o ID informado.
     */
    boolean exists(ServiceId id);

    /**
     * Busca todos os Services.
     */
    List<Service> findAll();

    /**
     * Remove um Service por ID.
     */
    void delete(ServiceId id);
}