package com.troquim_bot.repository;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import java.util.List;

/**
 * Repository abstraction para persistência de Business.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface BusinessRepository {

    /**
     * Salva um Business (cria ou atualiza).
     */
    Business save(Business business);

    /**
     * Busca um Business por ID.
     * 
     * @return Business se encontrado, null caso contrário
     */
    Business findById(BusinessId id);

    /**
     * Verifica se existe um Business com o ID informado.
     */
    boolean exists(BusinessId id);

    /**
     * Busca todos os Businesses.
     */
    List<Business> findAll();

    /**
     * Remove um Business por ID.
     */
    void delete(BusinessId id);
}