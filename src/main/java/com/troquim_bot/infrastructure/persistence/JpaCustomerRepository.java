package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;
import com.troquim_bot.repository.CustomerRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA que implementa CustomerRepository.
 *
 * Faz o mapeamento entre o aggregate Customer (domínio) e a entidade JPA
 * CustomerJpaEntity (infraestrutura), incluindo business_id e o phone_e164
 * canônico. Anotado com @Primary; é o único CustomerRepository de produção.
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
        return toDomain(saved);
    }

    @Override
    public Customer findById(CustomerId id) {
        if (id == null) {
            return null;
        }
        return springDataRepository.findById(id.getValue())
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public Optional<Customer> findByBusinessAndPhone(BusinessId businessId, PhoneNumber phone) {
        if (businessId == null || phone == null) {
            return Optional.empty();
        }
        return springDataRepository
                .findByBusinessIdAndPhoneE164(businessId.getValue(), phone.getE164())
                .map(this::toDomain);
    }

    @Override
    public List<Customer> findByBusinessId(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return springDataRepository.findByBusinessId(businessId.getValue()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(CustomerId id) {
        if (id == null) {
            return false;
        }
        return springDataRepository.existsById(id.getValue());
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
                customer.getBusinessId().getValue(),
                customer.getPhone().getE164(),
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

    private Customer toDomain(CustomerJpaEntity entity) {
        CustomerId customerId = CustomerId.from(entity.getId());
        BusinessId businessId = BusinessId.from(entity.getBusinessId());
        CustomerName name = new CustomerName(entity.getFirstName(), entity.getLastName());
        PhoneNumber phone = new PhoneNumber(entity.getPhone());
        CustomerStatus status = CustomerStatus.valueOf(entity.getStatus());

        return new Customer(
                customerId,
                businessId,
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
