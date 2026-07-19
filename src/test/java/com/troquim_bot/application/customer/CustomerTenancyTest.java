package com.troquim_bot.application.customer;

import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento por tenant e unicidade lógica do Customer (itens 2, 3, 5 e a
 * normalização E.164 no read-path), no nível de Application com o fake.
 */
class CustomerTenancyTest {

    private CustomerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerApplicationService(new InMemoryCustomerRepository());
    }

    @Test
    void mesmoTelefoneNoMesmoBusinessNaoCriaDuplicata() {
        service.criarCliente(TestTenants.PILOT, "Ana Paula", "5511999990001", null);

        assertThrows(IllegalArgumentException.class, () ->
                service.criarCliente(TestTenants.PILOT, "Ana Paula", "5511999990001", null));

        assertEquals(1, service.listarTodos(TestTenants.PILOT).size());
    }

    @Test
    void mesmoTelefoneEmBusinessDiferenteCriaDoisCustomersDistintos() {
        Customer noPilot = service.criarCliente(TestTenants.PILOT, "Ana Paula", "5511999990001", null);
        Customer noOutro = service.criarCliente(TestTenants.OUTRO, "Ana Paula", "5511999990001", null);

        assertNotEquals(noPilot.getId(), noOutro.getId());
        assertEquals(TestTenants.PILOT, noPilot.getBusinessId());
        assertEquals(TestTenants.OUTRO, noOutro.getBusinessId());
        assertEquals(1, service.listarTodos(TestTenants.PILOT).size());
        assertEquals(1, service.listarTodos(TestTenants.OUTRO).size());
    }

    @Test
    void queryDeUmBusinessNaoRetornaCustomerDeOutro() {
        service.criarCliente(TestTenants.PILOT, "Ana Paula", "5511999990001", null);
        service.criarCliente(TestTenants.OUTRO, "Bruno Costa", "5511999990002", null);

        List<Customer> doPilot = service.listarTodos(TestTenants.PILOT);
        assertEquals(1, doPilot.size());
        assertTrue(doPilot.stream().allMatch(c -> c.getBusinessId().equals(TestTenants.PILOT)));

        // O telefone do tenant OUTRO não é encontrado dentro do tenant PILOT.
        assertTrue(service.buscarPorTelefone(TestTenants.PILOT, new PhoneNumber("5511999990002")).isEmpty());
    }

    @Test
    void resolvePorTelefoneNormalizadoEmE164() {
        service.criarCliente(TestTenants.PILOT, "Ana Paula", "5511999990001", null);

        // Criado sem '+'; encontrado com '+' e separadores — mesma chave E.164.
        assertTrue(service.buscarPorTelefone(TestTenants.PILOT, new PhoneNumber("+55 11 99999-0001")).isPresent());
    }
}
