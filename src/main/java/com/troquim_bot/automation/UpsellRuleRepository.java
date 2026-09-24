package com.troquim_bot.automation;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;
import java.util.List;
import java.util.Optional;

public interface UpsellRuleRepository {
    Optional<Rule> find(BusinessId businessId, ServiceId baseServiceId);
    List<Rule> findByBusiness(BusinessId businessId);
    Rule save(Rule rule);
    void delete(BusinessId businessId, ServiceId baseServiceId);

    record Rule(BusinessId businessId, ServiceId baseServiceId,
                ServiceId addonServiceId, boolean active){}
}
