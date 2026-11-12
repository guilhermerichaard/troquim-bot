package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.marketplace.ApplicationStatus;
import com.troquim_bot.marketplace.EventApplication;
import com.troquim_bot.marketplace.EventRequest;
import com.troquim_bot.marketplace.EventRole;
import com.troquim_bot.marketplace.EventStatus;
import com.troquim_bot.repository.EventMarketplaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaEventMarketplaceRepository implements EventMarketplaceRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public EventRequest saveEvent(EventRequest event) {
        int updated = entityManager.createNativeQuery("""
                UPDATE marketplace_events
                   SET status = :status,
                       updated_at = :updated
                 WHERE id = :id
                """)
                .setParameter("status", event.getStatus().name())
                .setParameter("updated", event.getUpdatedAt())
                .setParameter("id", event.getId())
                .executeUpdate();
        if (updated == 0) {
            entityManager.createNativeQuery("""
                    INSERT INTO marketplace_events
                        (id,business_id,title,event_type,city,venue,starts_at,ends_at,budget_cents,status,created_at,updated_at)
                    VALUES
                        (:id,:business,:title,:eventType,:city,:venue,:startsAt,:endsAt,:budget,:status,:created,:updated)
                    """)
                    .setParameter("id", event.getId())
                    .setParameter("business", event.getBusinessId().getValue())
                    .setParameter("title", event.getTitle())
                    .setParameter("eventType", event.getEventType())
                    .setParameter("city", event.getCity())
                    .setParameter("venue", event.getVenue())
                    .setParameter("startsAt", event.getStartsAt())
                    .setParameter("endsAt", event.getEndsAt())
                    .setParameter("budget", event.getBudgetCents())
                    .setParameter("status", event.getStatus().name())
                    .setParameter("created", event.getCreatedAt())
                    .setParameter("updated", event.getUpdatedAt())
                    .executeUpdate();
        }
        return event;
    }

    @Override
    @Transactional
    public EventRole saveRole(EventRole role) {
        int updated = entityManager.createNativeQuery("""
                UPDATE marketplace_event_roles
                   SET filled_quantity = :filled
                 WHERE id = :id
                """)
                .setParameter("filled", role.getFilledQuantity())
                .setParameter("id", role.getId())
                .executeUpdate();
        if (updated == 0) {
            entityManager.createNativeQuery("""
                    INSERT INTO marketplace_event_roles
                        (id,event_id,role_name,quantity,filled_quantity,pay_cents,notes)
                    VALUES
                        (:id,:event,:roleName,:quantity,:filled,:pay,:notes)
                    """)
                    .setParameter("id", role.getId())
                    .setParameter("event", role.getEventId())
                    .setParameter("roleName", role.getRoleName())
                    .setParameter("quantity", role.getQuantity())
                    .setParameter("filled", role.getFilledQuantity())
                    .setParameter("pay", role.getPayCents())
                    .setParameter("notes", role.getNotes())
                    .executeUpdate();
        }
        return role;
    }

    @Override
    @Transactional
    public EventApplication saveApplication(EventApplication application) {
        int updated = entityManager.createNativeQuery("""
                UPDATE marketplace_applications
                   SET status = :status,
                       updated_at = :updated
                 WHERE id = :id
                """)
                .setParameter("status", application.getStatus().name())
                .setParameter("updated", application.getUpdatedAt())
                .setParameter("id", application.getId())
                .executeUpdate();
        if (updated == 0) {
            entityManager.createNativeQuery("""
                    INSERT INTO marketplace_applications
                        (id,event_id,role_id,applicant_name,applicant_phone,message,status,created_at,updated_at)
                    VALUES
                        (:id,:event,:role,:name,:phone,:message,:status,:created,:updated)
                    """)
                    .setParameter("id", application.getId())
                    .setParameter("event", application.getEventId())
                    .setParameter("role", application.getRoleId())
                    .setParameter("name", application.getApplicantName())
                    .setParameter("phone", application.getApplicantPhone())
                    .setParameter("message", application.getMessage())
                    .setParameter("status", application.getStatus().name())
                    .setParameter("created", application.getCreatedAt())
                    .setParameter("updated", application.getUpdatedAt())
                    .executeUpdate();
        }
        return application;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventRequest> findEvent(UUID eventId) {
        if (eventId == null) return Optional.empty();
        return rows("""
                SELECT id,business_id,title,event_type,city,venue,starts_at,ends_at,budget_cents,status,created_at,updated_at
                  FROM marketplace_events WHERE id=:id
                """, "id", eventId).stream().findFirst().map(this::mapEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventRole> findRole(UUID roleId) {
        if (roleId == null) return Optional.empty();
        return rows("""
                SELECT id,event_id,role_name,quantity,filled_quantity,pay_cents,notes
                  FROM marketplace_event_roles WHERE id=:id
                """, "id", roleId).stream().findFirst().map(this::mapRole);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventApplication> findApplication(UUID applicationId) {
        if (applicationId == null) return Optional.empty();
        return rows("""
                SELECT id,event_id,role_id,applicant_name,applicant_phone,message,status,created_at,updated_at
                  FROM marketplace_applications WHERE id=:id
                """, "id", applicationId).stream().findFirst().map(this::mapApplication);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventRequest> findByBusiness(BusinessId businessId) {
        return entityManager.createNativeQuery("""
                SELECT id,business_id,title,event_type,city,venue,starts_at,ends_at,budget_cents,status,created_at,updated_at
                  FROM marketplace_events
                 WHERE business_id=:business
                 ORDER BY starts_at DESC
                """)
                .setParameter("business", businessId.getValue())
                .getResultList().stream().map(this::mapEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventRequest> findOpenEvents() {
        return entityManager.createNativeQuery("""
                SELECT id,business_id,title,event_type,city,venue,starts_at,ends_at,budget_cents,status,created_at,updated_at
                  FROM marketplace_events
                 WHERE status IN ('OPEN','MATCHING')
                   AND ends_at > CURRENT_TIMESTAMP
                 ORDER BY starts_at
                """).getResultList().stream().map(this::mapEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventRole> findRoles(UUID eventId) {
        return entityManager.createNativeQuery("""
                SELECT id,event_id,role_name,quantity,filled_quantity,pay_cents,notes
                  FROM marketplace_event_roles
                 WHERE event_id=:event
                 ORDER BY role_name
                """)
                .setParameter("event", eventId)
                .getResultList().stream().map(this::mapRole).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventApplication> findApplications(UUID eventId) {
        return entityManager.createNativeQuery("""
                SELECT id,event_id,role_id,applicant_name,applicant_phone,message,status,created_at,updated_at
                  FROM marketplace_applications
                 WHERE event_id=:event
                 ORDER BY created_at
                """)
                .setParameter("event", eventId)
                .getResultList().stream().map(this::mapApplication).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allRolesFilled(UUID eventId) {
        Number open = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                  FROM marketplace_event_roles
                 WHERE event_id=:event AND filled_quantity < quantity
                """).setParameter("event", eventId).getSingleResult();
        return open.longValue() == 0;
    }

    private List<?> rows(String sql, String param, Object value) {
        return entityManager.createNativeQuery(sql).setParameter(param, value).getResultList();
    }

    private EventRequest mapEvent(Object raw) {
        Object[] r=(Object[])raw;
        return new EventRequest(uuid(r[0]),new BusinessId(uuid(r[1])),String.valueOf(r[2]),String.valueOf(r[3]),
                String.valueOf(r[4]),r[5]==null?null:String.valueOf(r[5]),dateTime(r[6]),dateTime(r[7]),
                r[8]==null?null:((Number)r[8]).longValue(),EventStatus.valueOf(String.valueOf(r[9])),
                dateTime(r[10]),dateTime(r[11]));
    }

    private EventRole mapRole(Object raw) {
        Object[] r=(Object[])raw;
        return new EventRole(uuid(r[0]),uuid(r[1]),String.valueOf(r[2]),((Number)r[3]).intValue(),
                ((Number)r[4]).intValue(),r[5]==null?null:((Number)r[5]).longValue(),r[6]==null?null:String.valueOf(r[6]));
    }

    private EventApplication mapApplication(Object raw) {
        Object[] r=(Object[])raw;
        return new EventApplication(uuid(r[0]),uuid(r[1]),uuid(r[2]),String.valueOf(r[3]),String.valueOf(r[4]),
                r[5]==null?null:String.valueOf(r[5]),ApplicationStatus.valueOf(String.valueOf(r[6])),
                dateTime(r[7]),dateTime(r[8]));
    }

    private static UUID uuid(Object v){ return v instanceof UUID u ? u : UUID.fromString(String.valueOf(v)); }
    private static LocalDateTime dateTime(Object v){
        if(v instanceof LocalDateTime d) return d;
        if(v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(v).replace(' ','T'));
    }
}
