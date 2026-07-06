package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;
import com.troquim_bot.repository.CustomerRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter JPA que implementa CustomerRepository.
 * 
 * Faz o mapeamento entre o aggregate Customer (domínio)
 * e a entidade JPA CustomerJpaEntity (infraestrutura).
 * 
 * Anotado com @Primary para ser usado pelo Spring por padrão,
 * enquanto InMemoryCustomerRepository pode ser usado em testes.
 */
@Repository
@Primary
public class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository springDataRepository;

    public JpaCustomerRepository(SpringDataCustomerRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Customer save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer não pode ser nulo");
        }
        CustomerJpaEntity entity = toEntity(customer);
        CustomerJpaEntity saved = springDataRepository.save(entity);
        return toDomain(saved, customer.getPhone());
    }

    @Override
    public Customer findById(CustomerId id) {
        if (id == null) {
            return null;
        }
        return springDataRepository.findById(id.getValue())
                .map(entity -> toDomain(entity, null))
                .orElse(null);
    }

    @Override
    public boolean exists(CustomerId id) {
        if (id == null) {
            return false;
        }
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public List<Customer> findAll() {
        return springDataRepository.findAll().stream()
                .map(entity -> toDomain(entity, null))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(CustomerId id) {
        if (id != null) {
            springDataRepository.deleteById(id.getValue());
        }
    }

    // ==================== MAPEAMENTO ====================

    private CustomerJpaEntity toEntity(Customer customer) {
        return new CustomerJpaEntity(
                customer.getId().getValue(),
                customer.getName().getFirstName(),
                customer.getName().getLastName(),
                customer.getPhone().getValue(),
                customer.getApelido(),
                customer.getNotes(),
                customer.getStatus().name(),
                customer.getTotalAtendimentos(),
                customer.getUltimoAtendimento(),
                customer.getCriadoEm(),
                customer.getAtualizadoEm()
        );
    }

    private Customer toDomain(CustomerJpaEntity entity, PhoneNumber phoneFallback) {
        CustomerId customerId = CustomerId.from(entity.getId());
        CustomerName name = new CustomerName(entity.getFirstName(), entity.getLastName());
        
        PhoneNumber phone = phoneFallback;
        if (phone == null && entity.getPhone() != null) {
            phone = new PhoneNumber(entity.getPhone());
        }
        if (phone == null) {
            phone = new PhoneNumber("+5500000000000");
        }

        CustomerStatus status = CustomerStatus.valueOf(entity.getStatus());

        return new Customer(
                customerId,
                name,
                phone,
                entity.getNotes(),
                entity.getApelido(),
                status,
                entity.getTotalAtendimentos(),
                entity.getUltimoAtendimento(),
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }
}