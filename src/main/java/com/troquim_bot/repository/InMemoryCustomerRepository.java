package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do CustomerRepository.
 *
 * Usada por testes unitários e pelo profile dev/test. Em produção, o
 * {@code JpaCustomerRepository} (anotado com @Primary) assume a persistência.
 */
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
    public Optional<Customer> findByBusinessAndPhone(BusinessId businessId, PhoneNumber phone) {
        if (businessId == null || phone == null) {
            return Optional.empty();
        }
        return customers.values().stream()
                .filter(c -> businessId.equals(c.getBusinessId()) && phone.getE164().equals(c.getPhone().getE164()))
                .findFirst();
    }

    @Override
    public List<Customer> findByBusinessId(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return customers.values().stream()
                .filter(c -> businessId.equals(c.getBusinessId()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(CustomerId id) {
        if (id == null) {
            return false;
        }
        return customers.containsKey(id);
    }

    @Override
    public void delete(CustomerId id) {
        if (id != null) {
            customers.remove(id);
        }
    }
}