package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.automation.UpsellRuleRepository;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaUpsellRuleRepository implements UpsellRuleRepository {
    private final SpringDataUpsellRuleRepository repository;

    public JpaUpsellRuleRepository(SpringDataUpsellRuleRepository repository){this.repository=repository;}

    @Override
    public Optional<Rule> find(BusinessId businessId,ServiceId baseServiceId){
        if(businessId==null||baseServiceId==null)return Optional.empty();
        return repository.findById(new UpsellRuleJpaEntity.Key(
                businessId.getValue(),baseServiceId.getValue())).map(UpsellRuleJpaEntity::toDomain);
    }

    @Override
    public List<Rule> findByBusiness(BusinessId businessId){
        if(businessId==null)return List.of();
        return repository.findByBusinessId(businessId.getValue()).stream()
                .map(UpsellRuleJpaEntity::toDomain).toList();
    }

    @Override
    public Rule save(Rule rule){return repository.save(UpsellRuleJpaEntity.from(rule)).toDomain();}

    @Override
    public void delete(BusinessId businessId,ServiceId baseServiceId){
        repository.deleteById(new UpsellRuleJpaEntity.Key(businessId.getValue(),baseServiceId.getValue()));
    }
}
