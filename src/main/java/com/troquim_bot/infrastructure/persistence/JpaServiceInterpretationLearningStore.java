package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.language.ServiceInterpretationLearningStore;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter SQL da memoria de interpretacao.
 *
 * O INSERT e idempotente pela UNIQUE (business_id, input_normalized). O UPDATE posterior
 * faz a memoria convergir para o ServiceId que o cliente confirmou mais recentemente e
 * incrementa evidencia. Nenhum texto de conversa, telefone ou nome de cliente e gravado.
 */
@Component
public class JpaServiceInterpretationLearningStore implements ServiceInterpretationLearningStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceId> buscar(BusinessId businessId, String entradaNormalizada) {
        if (businessId == null || entradaNormalizada == null || entradaNormalizada.isBlank()) {
            return Optional.empty();
        }

        List<?> ids = entityManager.createNativeQuery("""
                SELECT service_id
                  FROM service_interpretation_aliases
                 WHERE business_id = :business
                   AND input_normalized = :input
                 LIMIT 1
                """)
                .setParameter("business", businessId.getValue())
                .setParameter("input", entradaNormalizada)
                .getResultList();

        if (ids.isEmpty()) {
            return Optional.empty();
        }
        Object valor = ids.get(0);
        UUID id = valor instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(valor));
        return Optional.of(ServiceId.from(id));
    }

    @Override
    @Transactional
    public void aprender(BusinessId businessId, String entradaNormalizada, ServiceId serviceId) {
        if (businessId == null || serviceId == null
                || entradaNormalizada == null || entradaNormalizada.isBlank()) {
            return;
        }

        LocalDateTime agora = LocalDateTime.now();

        entityManager.createNativeQuery("""
                INSERT INTO service_interpretation_aliases
                    (id, business_id, input_normalized, service_id, confirmations, created_at, updated_at)
                VALUES (:id, :business, :input, :service, 0, :agora, :agora)
                ON CONFLICT DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("business", businessId.getValue())
                .setParameter("input", entradaNormalizada)
                .setParameter("service", serviceId.getValue())
                .setParameter("agora", agora)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE service_interpretation_aliases
                   SET service_id = :service,
                       confirmations = confirmations + 1,
                       updated_at = :agora
                 WHERE business_id = :business
                   AND input_normalized = :input
                """)
                .setParameter("service", serviceId.getValue())
                .setParameter("agora", agora)
                .setParameter("business", businessId.getValue())
                .setParameter("input", entradaNormalizada)
                .executeUpdate();
    }
}
