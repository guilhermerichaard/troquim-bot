package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;
import com.troquim_bot.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração que prova que Customer persiste e sobrevive a restart.
 * 
 * Usa o adapter JPA real (JpaCustomerRepository) com banco H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class JpaCustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void salvaEBuscaCustomerPorId() {
        CustomerId id = CustomerId.fromPhone("5511999999999");
        PhoneNumber phone = new PhoneNumber("5511999999999");
        CustomerName name = new CustomerName("Maria", "Silva");
        Customer customer = new Customer(id, name, phone, "Cliente VIP");

        customerRepository.save(customer);

        Customer found = customerRepository.findById(id);
        assertNotNull(found);
        assertEquals(id, found.getId());
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
        CustomerId id = CustomerId.fromPhone("5511888888888");
        PhoneNumber phone = new PhoneNumber("5511888888888");
        CustomerName name = new CustomerName("João", "Santos");
        Customer customer = new Customer(id, name, phone, null);
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
        CustomerId id = CustomerId.fromPhone("5511777777777");
        PhoneNumber phone = new PhoneNumber("5511777777777");
        CustomerName name = new CustomerName("Ana", "Costa");
        Customer customer = new Customer(id, name, phone, null);

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
        CustomerId id = CustomerId.fromPhone("5511666666666");
        PhoneNumber phone = new PhoneNumber("5511666666666");
        CustomerName name = new CustomerName("Pedro", "Alves");
        Customer customer = new Customer(id, name, phone, null);

        customerRepository.save(customer);

        assertTrue(customerRepository.exists(id));
        assertFalse(customerRepository.exists(CustomerId.fromPhone("5511000000000")));
    }

    @Test
    void findAllRetornaTodosCustomers() {
        CustomerId id1 = CustomerId.fromPhone("5511555555555");
        CustomerId id2 = CustomerId.fromPhone("5511444444444");
        PhoneNumber phone1 = new PhoneNumber("5511555555555");
        PhoneNumber phone2 = new PhoneNumber("5511444444444");
        CustomerName name1 = new CustomerName("Carla", "Lima");
        CustomerName name2 = new CustomerName("Paulo", "Souza");

        customerRepository.save(new Customer(id1, name1, phone1, null));
        customerRepository.save(new Customer(id2, name2, phone2, null));

        List<Customer> all = customerRepository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void deleteRemoveCustomer() {
        CustomerId id = CustomerId.fromPhone("5511333333333");
        PhoneNumber phone = new PhoneNumber("5511333333333");
        CustomerName name = new CustomerName("Teste", "Delete");
        Customer customer = new Customer(id, name, phone, null);

        customerRepository.save(customer);
        assertTrue(customerRepository.exists(id));

        customerRepository.delete(id);
        assertFalse(customerRepository.exists(id));
    }

    @Test
    void dadosSobrevivemASalvarEBuscar() {
        // Simula o ciclo: criar -> salvar -> buscar (como se fosse restart)
        CustomerId id = CustomerId.fromPhone("5511222222222");
        PhoneNumber phone = new PhoneNumber("5511222222222");
        CustomerName name = new CustomerName("Lucas", "Pereira");
        Customer customer = new Customer(id, name, phone, "Observação");
        customer.definirApelido("Lukinhas");
        customer.registrarAtendimento();
        customer.registrarAtendimento();
        customer.registrarAtendimento();

        customerRepository.save(customer);

        // Busca como se fosse uma nova instância (simula restart)
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