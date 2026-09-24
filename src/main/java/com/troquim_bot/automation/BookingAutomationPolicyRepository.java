package com.troquim_bot.automation;

import com.troquim_bot.business.BusinessId;

public interface BookingAutomationPolicyRepository {
    BookingAutomationPolicy get(BusinessId businessId);
    BookingAutomationPolicy save(BusinessId businessId, BookingAutomationPolicy policy);
}
