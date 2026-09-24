package com.troquim_bot.automation;

import com.troquim_bot.business.BusinessId;
import org.springframework.stereotype.Service;

@Service
public class BookingAutomationApplicationService {
    private final BookingAutomationPolicyRepository repository;

    public BookingAutomationApplicationService(BookingAutomationPolicyRepository repository) {
        this.repository=repository;
    }

    public BookingAutomationPolicy get(BusinessId businessId) {
        if(businessId==null) throw new IllegalArgumentException("BusinessId é obrigatório");
        return repository.get(businessId);
    }

    public BookingAutomationPolicy update(BusinessId businessId,
                                          boolean reminderEnabled,
                                          int reminderHoursBefore,
                                          int cancellationMinHours,
                                          boolean upsellEnabled) {
        return repository.save(businessId,new BookingAutomationPolicy(
                reminderEnabled,reminderHoursBefore,cancellationMinHours,upsellEnabled));
    }
}
