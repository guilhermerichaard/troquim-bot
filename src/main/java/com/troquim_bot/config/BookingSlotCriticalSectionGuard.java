package com.troquim_bot.config;

import com.troquim_bot.application.booking.BookingSlotCriticalSection;
import com.troquim_bot.infrastructure.persistence.PostgresBookingSlotCriticalSection;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Impede que a aplicação suba, fora de perfis de teste/dev, sem serialização REAL de slot.
 *
 * Um lock em memória fora desses perfis não protegeria nada: cada instância da aplicação
 * teria seu próprio lock, e duas instâncias concorrentes confirmando o mesmo horário
 * poderiam ambas passar — overbooking silencioso, sem um único erro no log. A guarda troca
 * essa degradação silenciosa por falha de inicialização explícita.
 *
 * Pequena e específica de propósito: não reutiliza {@code CatalogoPersistenceGuard} porque
 * esta não é uma guarda de PERSISTÊNCIA, é de SERIALIZAÇÃO — o Domain não sabe (nem precisa
 * saber) que isto existe.
 */
@Configuration
@Profile("!test & !dev-inmemory")
public class BookingSlotCriticalSectionGuard {

    public BookingSlotCriticalSectionGuard(BookingSlotCriticalSection bookingSlotCriticalSection) {
        if (!(bookingSlotCriticalSection instanceof PostgresBookingSlotCriticalSection)) {
            throw new IllegalStateException(
                    "Seção crítica de agenda sem serialização real: BookingSlotCriticalSection resolvido para "
                            + (bookingSlotCriticalSection == null
                                    ? "nenhum bean" : bookingSlotCriticalSection.getClass().getName())
                            + ", esperado " + PostgresBookingSlotCriticalSection.class.getSimpleName() + ". "
                            + "Fora dos perfis 'test' e 'dev-inmemory' a serialização de slot DEVE usar "
                            + "advisory lock do PostgreSQL — em memória, duas instâncias da aplicação não se "
                            + "serializariam entre si, permitindo overbooking.");
        }
    }
}
