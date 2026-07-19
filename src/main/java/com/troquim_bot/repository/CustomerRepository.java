package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction para persistência de Customer.
 *
 * Interface pura, sem dependência de frameworks. A implementação concreta
 * vive na camada de infraestrutura.
 *
 * ISOLAMENTO POR TENANT: toda consulta de listagem/telefone recebe um
 * {@link BusinessId} explícito. Não existe {@code findAll()} global (ver
 * ARCHITECTURE_V2_1 §C8). {@link #findById(CustomerId)} é permitido porque o
 * {@code CustomerId} é um surrogate globalmente único.
 */
public interface CustomerRepository {

    /**
     * Salva um Customer (cria ou atualiza). O Customer carrega seu BusinessId.
     */
    Customer save(Customer customer);

    /**
     * Busca um Customer pelo seu id surrogate (globalmente único).
     *
     * @return Customer se encontrado, null caso contrário
     */
    Customer findById(CustomerId id);

    /**
     * Resolve o Customer de um tenant pela chave lógica (BusinessId, phoneE164).
     * É a base do resolve-or-create e garante a unicidade por tenant.
     */
    Optional<Customer> findByBusinessAndPhone(BusinessId businessId, PhoneNumber phone);

    /**
     * Lista os Customers de um único tenant. Substitui o antigo findAll global.
     */
    List<Customer> findByBusinessId(BusinessId businessId);

    /**
     * Verifica se existe um Customer com o id informado.
     */
    boolean exists(CustomerId id);

    /**
     * Remove um Customer por id.
     */
    void delete(CustomerId id);
}
