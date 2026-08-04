package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingSlotCriticalSection;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Duplo em memória da seção crítica de slot, com {@link ReentrantLock} por chave.
 *
 * LEGÍTIMO SOMENTE em {@code test} e {@code dev-inmemory}: fora deles, um lock local não
 * serializa entre instâncias distintas da aplicação — exatamente o cenário que permitiria
 * overbooking em produção. Por isso a guarda de persistência derruba o startup fora desses
 * perfis (ver {@code BookingSlotCriticalSectionGuard}).
 *
 * {@link ReentrantLock}, não {@code synchronized}: o lock precisa ser adquirido ANTES do
 * Supplier rodar e ficar de pé durante toda a execução dele, com liberação garantida em
 * {@code finally} — o mesmo contrato do advisory lock do Postgres, só que local.
 */
@Component
@Profile({"test", "dev-inmemory"})
public class InMemoryBookingSlotCriticalSection implements BookingSlotCriticalSection {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T executar(BusinessId businessId, ProfessionalId professionalId, LocalDate date, Supplier<T> action) {
        if (businessId == null || professionalId == null || date == null) {
            throw new IllegalArgumentException(
                    "BusinessId, ProfessionalId e LocalDate são obrigatórios para a seção crítica do slot");
        }
        if (action == null) {
            throw new IllegalArgumentException("action é obrigatória");
        }

        String chave = businessId.getValue() + "|" + professionalId.getValue() + "|" + date;
        ReentrantLock lock = locks.computeIfAbsent(chave, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
