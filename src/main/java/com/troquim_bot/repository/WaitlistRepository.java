package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.waitlist.WaitlistEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository {

    WaitlistEntry save(WaitlistEntry entry);

    Optional<WaitlistEntry> findById(UUID id);

    List<WaitlistEntry> findActiveByBusiness(BusinessId businessId);

    Optional<WaitlistEntry> findActiveRequest(BusinessId businessId,
                                              String phoneE164,
                                              ServiceId serviceId,
                                              ProfessionalId professionalId);
}
