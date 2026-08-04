package com.troquim_bot.repository;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

/**
 * Repository abstraction para persistência de Business — a raiz de identidade do tenant.
 *
 * Interface pura, sem dependência de frameworks. A implementação concreta é definida na
 * camada de infraestrutura.
 *
 * SEM SELEÇÃO GLOBAL DE PROPÓSITO: não existe {@code findAll}. Toda operação administrativa
 * exige o {@link BusinessId} de qual negócio está sendo consultado — a fantasia de "o
 * Business atual" (MVP de um salão só) não escala para um sistema multi-tenant, e um
 * `findFirst()` sobre todos os negócios vazaria o negócio errado assim que houvesse dois.
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
}
