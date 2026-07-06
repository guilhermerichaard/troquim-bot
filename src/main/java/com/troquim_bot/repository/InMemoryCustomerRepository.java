package com.troquim_bot.repository;

import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do CustomerRepository.
 */
@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private final ConcurrentMap<CustomerId, Customer> customers = new ConcurrentHashMap<>();

    @Override
    public Customer save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer não pode ser nulo");
        }
        customers.put(customer.getId(), customer);
        return customer;
    }

    @Override
    public Customer findById(CustomerId id) {
        if (id == null) {
            return null;
        }
        return customers.get(id);
    }

    @Override
    public boolean exists(CustomerId id) {
        if (id == null) {
            return false;
        }
        return customers.containsKey(id);
    }

    @Override
    public List<Customer> findAll() {
        return new ArrayList<>(customers.values());
    }

    @Override
    public void delete(CustomerId id) {
        if (id != null) {
            customers.remove(id);
        }
    }
}