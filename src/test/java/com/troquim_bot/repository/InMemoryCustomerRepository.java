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

/**
 * Fake em memória de {@link CustomerRepository} para uso EXCLUSIVO em testes.
 *
 * Não é um componente Spring (sem @Repository/@Profile) e vive em src/test:
 * nenhum código de produção depende dele. Em produção o único CustomerRepository
 * é o JpaCustomerRepository (@Primary). Reproduz o isolamento por tenant
 * (BusinessId) e a unicidade lógica por (BusinessId, phoneE164) do repositório
 * real, para os testes de lógica que não exigem o PostgreSQL.
 */
public class InMemoryCustomerRepository implements CustomerRepository {

    private final ConcurrentMap<CustomerId, Customer> customers = new ConcurrentHashMap<>();

    @Override
    public Customer save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer não pode ser nulo");
        }
        // Emula a UNIQUE (business_id, phone_e164): rejeita um segundo id para a mesma chave.
        String key = tenantPhoneKey(customer.getBusinessId(), customer.getPhone());
        for (Customer existing : customers.values()) {
            if (!existing.getId().equals(customer.getId())
                    && tenantPhoneKey(existing.getBusinessId(), existing.getPhone()).equals(key)) {
                throw new IllegalStateException(
                        "Violação de unicidade (business_id, phone_e164): " + key);
            }
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
        String key = tenantPhoneKey(businessId, phone);
        return customers.values().stream()
                .filter(c -> tenantPhoneKey(c.getBusinessId(), c.getPhone()).equals(key))
                .findFirst();
    }

    @Override
    public List<Customer> findByBusinessId(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        List<Customer> result = new ArrayList<>();
        for (Customer c : customers.values()) {
            if (c.getBusinessId().equals(businessId)) {
                result.add(c);
            }
        }
        return result;
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

    private static String tenantPhoneKey(BusinessId businessId, PhoneNumber phone) {
        return businessId.getValue() + "|" + phone.getE164();
    }
}
