package com.troquim_bot.repository;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import java.util.List;

/**
 * Repository abstraction para persistência de Business — a raiz de identidade do tenant.
 *
 * Interface pura, sem dependência de frameworks. A implementação concreta é definida na
 * camada de infraestrutura.
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
     * Busca todos os Businesses. Usado apenas pela área administrativa legada de
     * single-salão; nenhum caso de uso tenant-scoped deve depender disto.
     */
    List<Business> findAll();
}
