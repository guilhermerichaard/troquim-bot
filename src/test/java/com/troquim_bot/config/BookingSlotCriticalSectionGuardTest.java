package com.troquim_bot.config;

import com.troquim_bot.application.booking.BookingSlotCriticalSection;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.infrastructure.persistence.InMemoryBookingSlotCriticalSection;
import com.troquim_bot.infrastructure.persistence.PostgresBookingSlotCriticalSection;
import com.troquim_bot.professional.ProfessionalId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 17. O adapter em memória da seção crítica de slot não pode iniciar em produção — um lock
 * local não serializa entre instâncias da aplicação, permitindo overbooking silencioso.
 */
@DisplayName("Guarda de serialização de slot de agenda")
class BookingSlotCriticalSectionGuardTest {

    private static BookingSlotCriticalSection volatilLocal() {
        return new BookingSlotCriticalSection() {
            @Override
            public <T> T executar(BusinessId businessId, ProfessionalId professionalId,
                                  LocalDate date, Supplier<T> action) {
                return action.get();
            }
        };
    }

    @Test
    @DisplayName("recusa subir quando a seção crítica não é o adapter PostgreSQL")
    void recusaAdapterVolatil() {
        assertThatThrownBy(() -> new BookingSlotCriticalSectionGuard(volatilLocal()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BookingSlotCriticalSection")
                .hasMessageContaining("overbooking");
    }

    @Test
    @DisplayName("recusa subir quando não há bean nenhum resolvido")
    void recusaAusencia() {
        assertThatThrownBy(() -> new BookingSlotCriticalSectionGuard(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum bean");
    }

    @Test
    @DisplayName("aceita quando a seção crítica é o adapter PostgreSQL")
    void aceitaAdapterPostgres() {
        assertThatCode(() -> new BookingSlotCriticalSectionGuard(
                Mockito.mock(PostgresBookingSlotCriticalSection.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o adapter em memória está restrito aos perfis test e dev-inmemory")
    void adapterEmMemoriaTemPerfilRestrito() {
        var perfil = InMemoryBookingSlotCriticalSection.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(perfil).isNotNull();
        assertThat(perfil.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
    }

    @Test
    @DisplayName("o adapter PostgreSQL está restrito FORA dos perfis test e dev-inmemory")
    void adapterPostgresNaoRodaEmTestOuDevInmemory() {
        var perfil = PostgresBookingSlotCriticalSection.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(perfil).isNotNull();
        assertThat(perfil.value()).containsExactly("!test & !dev-inmemory");
    }

    // ==================== 19. nenhuma regra de agenda na Infrastructure ====================

    @Test
    @DisplayName("19. nenhum dos dois adapters depende de ConsultarDisponibilidade ou de regra de calendário")
    void adaptersNaoDependemDeRegraDeAgenda() {
        for (Class<?> classeProibida : java.util.List.of(
                com.troquim_bot.application.availability.ConsultarDisponibilidade.class,
                com.troquim_bot.business.BusinessHours.class,
                com.troquim_bot.availability.IntervaloDeHorario.class,
                com.troquim_bot.repository.BusinessCalendarRepository.class,
                com.troquim_bot.repository.AvailabilityRepository.class)) {
            assertThat(dependeDe(PostgresBookingSlotCriticalSection.class, classeProibida))
                    .as("PostgresBookingSlotCriticalSection não pode depender de %s — só trava, não decide",
                            classeProibida.getSimpleName())
                    .isFalse();
            assertThat(dependeDe(InMemoryBookingSlotCriticalSection.class, classeProibida))
                    .as("InMemoryBookingSlotCriticalSection não pode depender de %s — só trava, não decide",
                            classeProibida.getSimpleName())
                    .isFalse();
        }
    }

    private static boolean dependeDe(Class<?> adapter, Class<?> tipoProibido) {
        for (var campo : adapter.getDeclaredFields()) {
            if (campo.getType().equals(tipoProibido)) {
                return true;
            }
        }
        for (var construtor : adapter.getDeclaredConstructors()) {
            for (Class<?> parametro : construtor.getParameterTypes()) {
                if (parametro.equals(tipoProibido)) {
                    return true;
                }
            }
        }
        return false;
    }
}
