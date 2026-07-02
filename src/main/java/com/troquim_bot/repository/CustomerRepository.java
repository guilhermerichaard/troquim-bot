package com.troquim_bot.repository;

import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;

import java.util.List;

/**
 * Repository abstraction para persistência de Customer.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface CustomerRepository {

    /**
     * Salva um Customer (cria ou atualiza).
     */
    Customer save(Customer customer);

    /**
     * Busca um Customer por ID.
     * 
     * @return Customer se encontrado, null caso contrário
     */
    Customer findById(CustomerId id);

    /**
     * Verifica se existe um Customer com o ID informado.
     */
    boolean exists(CustomerId id);

    /**
     * Busca todos os Customers.
     */
    List<Customer> findAll();

    /**
     * Remove um Customer por ID.
     */
    void delete(CustomerId id);
}