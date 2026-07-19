package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a fronteira transacional de {@link BookingApplicationService#confirmar}
 * (ARCHITECTURE_V2_1 §C10) contra PostgreSQL real (Testcontainers), SEM H2.
 *
 * Reservation, Appointment e Customer devem ser persistidos numa ÚNICA transação
 * Spring/JPA. Um {@link CustomerRepository} test-only decora o repositório real:
 * delega as leituras e lança RuntimeException no {@code save} — simulando a falha
 * ao persistir o Customer. Sem hooks de teste no código produtivo.
 *
 * Ordem do fluxo (preservada): Reservation e Appointment são gravados ANTES do
 * Customer; a falha no Customer deve reverter TUDO.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@Import(BookingConfirmationRollbackPostgresTest.ThrowingCustomerRepoConfig.class)
class BookingConfirmationRollbackPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // azure exige o tenant do piloto e a chave administrativa (sem default).
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> "11111111-1111-1111-1111-111111111111");
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
    }

    @Autowired
    private BookingApplicationService booking;              // bean proxied → @Transactional aplica
    @Autowired
    private CustomerRepository customerRepository;          // o decorator @Primary
    @Autowired
    private SpringDataCustomerRepository customers;
    @Autowired
    private SpringDataReservationRepository reservations;
    @Autowired
    private SpringDataAppointmentRepository appointments;
    @Autowired
    private RollbackProbe probe;

    @Test
    void falhaAoPersistirCustomerFazRollbackDeReservationEAppointment() {
        String telefone = "5511977770001";                 // fictício

        // Base limpa (container novo).
        assertEquals(0, customers.count());
        assertEquals(0, reservations.count());
        assertEquals(0, appointments.count());

        // 3. A persistência do Customer lança RuntimeException → propaga por confirmar.
        assertThrows(RuntimeException.class, () ->
                booking.confirmar(telefone, "Cliente Rollback", "cabelo", "sexta", "13h"));

        // 1. A confirmação rodou dentro de uma transação Spring/JPA real.
        assertTrue(probe.transacaoAtiva,
                "confirmar deve executar dentro de uma transação real");

        // 2. Reservation e Appointment já estavam persistidos ANTES da falha do Customer
        //    (count() força o flush do persistence context na mesma transação).
        assertEquals(1, probe.reservationsNoSave,
                "Reservation deve estar persistida antes da falha do Customer");
        assertEquals(1, probe.appointmentsNoSave,
                "Appointment deve estar persistido antes da falha do Customer");

        // 4. Após o rollback: nada permanece.
        assertEquals(0, customers.count(), "Nenhum Customer novo após rollback");
        assertEquals(0, reservations.count(), "Nenhuma Reservation após rollback");
        assertEquals(0, appointments.count(), "Nenhum Appointment após rollback");
        assertTrue(customerRepository.findByBusinessAndPhone(TestTenants.PILOT, new PhoneNumber(telefone)).isEmpty(),
                "Nenhum Customer deve existir para o telefone após o rollback");
    }

    /** Captura o estado observado no momento do save do Customer. */
    static class RollbackProbe {
        volatile boolean transacaoAtiva;
        volatile long reservationsNoSave = -1;
        volatile long appointmentsNoSave = -1;
    }

    @TestConfiguration
    static class ThrowingCustomerRepoConfig {

        @Bean
        RollbackProbe rollbackProbe() {
            return new RollbackProbe();
        }

        /**
         * Envolve o {@code jpaCustomerRepository} de produção pelo decorator, sem
         * redefinir o bean (evita bean-definition override). As leituras vão ao
         * repositório real; o save lança para simular a falha ao persistir Customer.
         */
        @Bean
        static BeanPostProcessor throwOnSaveCustomerRepoDecorator(
                ObjectProvider<RollbackProbe> probe,
                ObjectProvider<SpringDataReservationRepository> reservations,
                ObjectProvider<SpringDataAppointmentRepository> appointments) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof JpaCustomerRepository real) {
                        return new ThrowOnSaveCustomerRepository(
                                real, reservations.getObject(), appointments.getObject(), probe.getObject());
                    }
                    return bean;
                }
            };
        }
    }

    /** Decorator test-only: delega leituras; no {@code save} captura o estado e lança. */
    static class ThrowOnSaveCustomerRepository implements CustomerRepository {

        private final CustomerRepository delegate;
        private final SpringDataReservationRepository reservations;
        private final SpringDataAppointmentRepository appointments;
        private final RollbackProbe probe;

        ThrowOnSaveCustomerRepository(CustomerRepository delegate,
                                      SpringDataReservationRepository reservations,
                                      SpringDataAppointmentRepository appointments,
                                      RollbackProbe probe) {
            this.delegate = delegate;
            this.reservations = reservations;
            this.appointments = appointments;
            this.probe = probe;
        }

        @Override
        public Customer save(Customer customer) {
            probe.transacaoAtiva = TransactionSynchronizationManager.isActualTransactionActive();
            // count() dispara o auto-flush → enxerga os inserts pendentes da MESMA transação.
            probe.reservationsNoSave = reservations.count();
            probe.appointmentsNoSave = appointments.count();
            throw new IllegalStateException("Falha simulada ao persistir Customer");
        }

        @Override
        public Customer findById(CustomerId id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<Customer> findByBusinessAndPhone(BusinessId businessId, PhoneNumber phone) {
            return delegate.findByBusinessAndPhone(businessId, phone);
        }

        @Override
        public List<Customer> findByBusinessId(BusinessId businessId) {
            return delegate.findByBusinessId(businessId);
        }

        @Override
        public boolean exists(CustomerId id) {
            return delegate.exists(id);
        }

        @Override
        public void delete(CustomerId id) {
            delegate.delete(id);
        }
    }
}
