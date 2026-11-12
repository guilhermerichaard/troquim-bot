package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.marketplace.EventApplication;
import com.troquim_bot.marketplace.EventRequest;
import com.troquim_bot.marketplace.EventRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventMarketplaceRepository {
    EventRequest saveEvent(EventRequest event);
    EventRole saveRole(EventRole role);
    EventApplication saveApplication(EventApplication application);
    Optional<EventRequest> findEvent(UUID eventId);
    Optional<EventRole> findRole(UUID roleId);
    Optional<EventApplication> findApplication(UUID applicationId);
    List<EventRequest> findByBusiness(BusinessId businessId);
    List<EventRequest> findOpenEvents();
    List<EventRole> findRoles(UUID eventId);
    List<EventApplication> findApplications(UUID eventId);
    boolean allRolesFilled(UUID eventId);
}
