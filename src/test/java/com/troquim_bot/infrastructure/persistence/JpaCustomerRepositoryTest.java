package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração do adapter JPA real (JpaCustomerRepository) com H2.
 *
 * @Transactional isola cada método (rollback), evitando acúmulo no H2 in-memory
 * compartilhado entre @SpringBootTest — relevante agora que há UNIQUE
 * (business_id, phone_e164). A prova de persistência real entre reinícios está
 * em CustomerPostgresPersistenceTest (PostgreSQL/Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaCustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void salvaEBuscaCustomerPorId() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511999999999");
        CustomerName name = new CustomerName("Maria", "Silva");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, "Cliente VIP");

        customerRepository.save(customer);

        Customer found = customerRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals(TestTenants.PILOT, found.getBusinessId());
        assertEquals("Maria", found.getName().getFirstName());
        assertEquals("Silva", found.getName().getLastName());
        assertEquals("5511999999999", found.getPhone().getValue());
        assertEquals("Cliente VIP", found.getNotes());
        assertEquals(CustomerStatus.ATIVO, found.getStatus());
        assertEquals(0, found.getTotalAtendimentos());
        assertNull(found.getUltimoAtendimento());
        assertNotNull(found.getCriadoEm());
        assertNotNull(found.getAtualizadoEm());
    }

    @Test
    void salvaEBuscaCustomerComApelidoEAtendimentos() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511888888888");
        CustomerName name = new CustomerName("João", "Santos");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, null);
        customer.definirApelido("Joãozinho");
        customer.registrarAtendimento();
        customer.registrarAtendimento();

        customerRepository.save(customer);

        Customer found = customerRepository.findById(id);
        assertNotNull(found);
        assertEquals("Joãozinho", found.getApelido());
        assertEquals(2, found.getTotalAtendimentos());
        assertNotNull(found.getUltimoAtendimento());
    }

    @Test
    void atualizaCustomerExistente() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511777777777");
        CustomerName name = new CustomerName("Ana", "Costa");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, null);

        customerRepository.save(customer);

        Customer saved = customerRepository.findById(id);
        saved.atualizarNome(new CustomerName("Ana", "Carvalho"));
        saved.definirApelido("Aninha");
        saved.registrarAtendimento();
        customerRepository.save(saved);

        Customer updated = customerRepository.findById(id);
        assertEquals("Ana", updated.getName().getFirstName());
        assertEquals("Carvalho", updated.getName().getLastName());
        assertEquals("Aninha", updated.getApelido());
        assertEquals(1, updated.getTotalAtendimentos());
    }

    @Test
    void existsRetornaTrueParaCustomerExistente() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511666666666");
        CustomerName name = new CustomerName("Pedro", "Alves");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, null);

        customerRepository.save(customer);

        assertTrue(customerRepository.exists(id));
        assertFalse(customerRepository.exists(CustomerId.generate()));
    }

    @Test
    void findByBusinessIdRetornaClientesDoTenant() {
        Customer c1 = new Customer(CustomerId.generate(), TestTenants.PILOT,
                new CustomerName("Carla", "Lima"), new PhoneNumber("5511555555555"), null);
        Customer c2 = new Customer(CustomerId.generate(), TestTenants.PILOT,
                new CustomerName("Paulo", "Souza"), new PhoneNumber("5511444444444"), null);

        customerRepository.save(c1);
        customerRepository.save(c2);

        List<Customer> doTenant = customerRepository.findByBusinessId(TestTenants.PILOT);
        assertEquals(2, doTenant.size());
    }

    @Test
    void deleteRemoveCustomer() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511333333333");
        CustomerName name = new CustomerName("Teste", "Delete");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, null);

        customerRepository.save(customer);
        assertTrue(customerRepository.exists(id));

        customerRepository.delete(id);
        assertFalse(customerRepository.exists(id));
    }

    @Test
    void dadosSobrevivemASalvarEBuscar() {
        CustomerId id = CustomerId.generate();
        PhoneNumber phone = new PhoneNumber("5511222222222");
        CustomerName name = new CustomerName("Lucas", "Pereira");
        Customer customer = new Customer(id, TestTenants.PILOT, name, phone, "Observação");
        customer.definirApelido("Lukinhas");
        customer.registrarAtendimento();
        customer.registrarAtendimento();
        customer.registrarAtendimento();

        customerRepository.save(customer);

        Customer found = customerRepository.findById(id);
        assertNotNull(found);
        assertEquals("Lucas", found.getName().getFirstName());
        assertEquals("Pereira", found.getName().getLastName());
        assertEquals("Lukinhas", found.getApelido());
        assertEquals(3, found.getTotalAtendimentos());
        assertNotNull(found.getUltimoAtendimento());
        assertEquals("Observação", found.getNotes());
        assertEquals(CustomerStatus.ATIVO, found.getStatus());
    }
}
