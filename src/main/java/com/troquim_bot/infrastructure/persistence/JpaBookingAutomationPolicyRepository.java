package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.automation.BookingAutomationPolicy;
import com.troquim_bot.automation.BookingAutomationPolicyRepository;
import com.troquim_bot.business.BusinessId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaBookingAutomationPolicyRepository implements BookingAutomationPolicyRepository {
    private final SpringDataBookingAutomationPolicyRepository repository;

    public JpaBookingAutomationPolicyRepository(SpringDataBookingAutomationPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly=true)
    public BookingAutomationPolicy get(BusinessId businessId) {
        if (businessId == null) return BookingAutomationPolicy.padrao();
        return repository.findById(businessId.getValue())
                .map(BookingAutomationPolicyJpaEntity::toDomain)
                .orElseGet(BookingAutomationPolicy::padrao);
    }

    @Override
    @Transactional
    public BookingAutomationPolicy save(BusinessId businessId, BookingAutomationPolicy policy) {
        if (businessId == null || policy == null) throw new IllegalArgumentException("Negócio e política são obrigatórios");
        return repository.save(BookingAutomationPolicyJpaEntity.of(businessId.getValue(), policy)).toDomain();
    }
}
