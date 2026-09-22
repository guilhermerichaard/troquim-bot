package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.WaitlistRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.waitlist.WaitlistEntry;
import com.troquim_bot.waitlist.WaitlistStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaWaitlistRepository implements WaitlistRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public WaitlistEntry save(WaitlistEntry entry) {
        int updated = entityManager.createNativeQuery("""
                UPDATE booking_waitlist
                   SET status = :status,
                       notified_at = :notified
                 WHERE id = :id
                """)
                .setParameter("status", entry.getStatus().name())
                .setParameter("notified", entry.getNotifiedAt())
                .setParameter("id", entry.getId())
                .executeUpdate();

        if (updated == 0) {
            entityManager.createNativeQuery("""
                    INSERT INTO booking_waitlist
                        (id, business_id, phone_e164, service_id, professional_id,
                         requested_date, earliest_time, latest_time, status, created_at, notified_at)
                    VALUES
                        (:id, :business, :phone, :service, :professional,
                         :requestedDate, :earliest, :latest, :status, :createdAt, :notified)
                    """)
                    .setParameter("id", entry.getId())
                    .setParameter("business", entry.getBusinessId().getValue())
                    .setParameter("phone", entry.getPhoneE164())
                    .setParameter("service", entry.getServiceId().getValue())
                    .setParameter("professional", entry.getProfessionalId().getValue())
                    .setParameter("requestedDate", entry.getRequestedDate())
                    .setParameter("earliest", entry.getEarliestTime())
                    .setParameter("latest", entry.getLatestTime())
                    .setParameter("status", entry.getStatus().name())
                    .setParameter("createdAt", entry.getCreatedAt())
                    .setParameter("notified", entry.getNotifiedAt())
                    .executeUpdate();
        }
        return entry;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WaitlistEntry> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id, business_id, phone_e164, service_id, professional_id,
                       requested_date, earliest_time, latest_time, status, created_at, notified_at
                  FROM booking_waitlist
                 WHERE id = :id
                 LIMIT 1
                """)
                .setParameter("id", id)
                .getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistEntry> findActiveByBusiness(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return entityManager.createNativeQuery("""
                SELECT id, business_id, phone_e164, service_id, professional_id,
                       requested_date, earliest_time, latest_time, status, created_at, notified_at
                  FROM booking_waitlist
                 WHERE business_id = :business
                   AND status = 'ACTIVE'
                 ORDER BY created_at
                """)
                .setParameter("business", businessId.getValue())
                .getResultList().stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WaitlistEntry> findActiveRequest(BusinessId businessId,
                                                     String phoneE164,
                                                     ServiceId serviceId,
                                                     ProfessionalId professionalId) {
        if (businessId == null || phoneE164 == null || serviceId == null || professionalId == null) {
            return Optional.empty();
        }
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id, business_id, phone_e164, service_id, professional_id,
                       requested_date, earliest_time, latest_time, status, created_at, notified_at
                  FROM booking_waitlist
                 WHERE business_id = :business
                   AND phone_e164 = :phone
                   AND service_id = :service
                   AND professional_id = :professional
                   AND status = 'ACTIVE'
                 ORDER BY created_at
                 LIMIT 1
                """)
                .setParameter("business", businessId.getValue())
                .setParameter("phone", phoneE164)
                .setParameter("service", serviceId.getValue())
                .setParameter("professional", professionalId.getValue())
                .getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    private WaitlistEntry map(Object raw) {
        Object[] row = (Object[]) raw;
        return new WaitlistEntry(
                uuid(row[0]),
                new BusinessId(uuid(row[1])),
                String.valueOf(row[2]),
                ServiceId.from(uuid(row[3])),
                ProfessionalId.from(uuid(row[4])),
                date(row[5]),
                time(row[6]),
                time(row[7]),
                WaitlistStatus.valueOf(String.valueOf(row[8])),
                dateTime(row[9]),
                dateTime(row[10]));
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(value));
    }

    private static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private static LocalTime time(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime time) return time;
        if (value instanceof java.sql.Time time) return time.toLocalTime();
        return LocalTime.parse(String.valueOf(value));
    }

    private static LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}
