package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingSlotCriticalSection;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Adapter de PRODUÇÃO da seção crítica de slot: {@code pg_advisory_xact_lock} do PostgreSQL.
 *
 * POR QUE ADVISORY LOCK, NÃO LOCK LOCAL: um lock em memória da JVM só serializa dentro do
 * MESMO processo. Com mais de uma instância da aplicação (o caso normal em produção), duas
 * instâncias distintas confirmando o mesmo slot ao mesmo tempo não se veriam — cada uma
 * teria seu próprio lock, e a corrida continuaria intacta. O advisory lock vive no
 * PostgreSQL, então serializa entre QUALQUER número de instâncias.
 *
 * {@code xact}: o lock é liberado automaticamente no COMMIT ou ROLLBACK da transação — nunca
 * há um "unlock" explícito, e uma exceção que aborte a transação libera o lock do mesmo jeito
 * que um retorno normal.
 *
 * {@code Propagation.MANDATORY}: adquirir o lock fora de uma transação não faria sentido —
 * ele teria vida útil indefinida (nunca seria liberado). Exigir transação existente também
 * PROVA que o Supplier roda na mesma transação que o resto do caso de uso.
 *
 * CHAVE DETERMINÍSTICA: os 8 primeiros bytes de SHA-256 de
 * {@code businessId|professionalId|date}, lidos como {@code long}. Uma colisão de hash entre
 * slots DIFERENTES apenas os serializaria um atrás do outro — nunca deixaria dois
 * agendamentos conflitantes passarem, porque a serialização é conservadora por natureza
 * (travar demais é inofensivo; travar de menos não é).
 *
 * FORA de {@code test}/{@code dev-inmemory} DE PROPÓSITO, sem {@code @Primary}: diferente
 * dos outros adapters JPA do sistema, {@code pg_advisory_xact_lock} é uma função exclusiva
 * do PostgreSQL — o H2 do profile de teste não a implementa, nem em {@code MODE=PostgreSQL}.
 * Um {@code @Primary} sem restrição de perfil faria todo teste em H2 estourar tentando
 * chamar uma função inexistente. A exclusão mútua de perfil com
 * {@link InMemoryBookingSlotCriticalSection} garante exatamente um candidato em cada
 * ambiente, sem depender de {@code @Primary} para desempatar.
 */
@Component
@Profile("!test & !dev-inmemory")
public class PostgresBookingSlotCriticalSection implements BookingSlotCriticalSection {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T executar(BusinessId businessId, ProfessionalId professionalId, LocalDate date, Supplier<T> action) {
        if (businessId == null || professionalId == null || date == null) {
            throw new IllegalArgumentException(
                    "BusinessId, ProfessionalId e LocalDate são obrigatórios para a seção crítica do slot");
        }
        if (action == null) {
            throw new IllegalArgumentException("action é obrigatória");
        }

        long chave = chaveDeterministica(businessId, professionalId, date);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:chave)")
                .setParameter("chave", chave)
                .getSingleResult();

        return action.get();
    }

    private static long chaveDeterministica(BusinessId businessId, ProfessionalId professionalId, LocalDate date) {
        String canonico = businessId.getValue() + "|" + professionalId.getValue() + "|" + date;
        byte[] hash = sha256(canonico);
        return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
