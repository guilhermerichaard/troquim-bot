package com.troquim_bot.application.customer;

import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.infrastructure.persistence.JpaCustomerRepository;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que CustomerApplicationService (read-path de GET /customers) usa o
 * MESMO CustomerRepository que CustomerProfileService (write-path do fluxo de
 * conversa). Regressão do bug em que um construtor no-arg criava um
 * InMemoryCustomerRepository desconectado e /customers ficava sempre vazio.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerApplicationServiceWiringTest {

    @Autowired
    private CustomerProfileService customerProfileService;

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Confirma, no profile "test", qual bean CustomerRepository é injetado: deve
     * ser o JpaCustomerRepository @Primary (H2). O InMemoryCustomerRepository não
     * é mais um componente Spring (é um fake de teste em src/test), portanto NÃO
     * existe como bean em nenhum profile. Assim o contexto tem uma ÚNICA fonte de
     * verdade de Customer (JPA), sem repositório em memória concorrente.
     */
    @Test
    void noProfileDeTesteOCustomerRepositoryInjetadoEhJpaEInMemoryNaoEhBean() {
        assertInstanceOf(JpaCustomerRepository.class, customerRepository,
                "No profile 'test', o CustomerRepository @Primary injetado deve ser o JpaCustomerRepository");
        assertEquals(0, applicationContext.getBeanNamesForType(InMemoryCustomerRepository.class).length,
                "InMemoryCustomerRepository não deve existir como bean Spring (é um fake de teste, não @Repository)");
    }

    @Test
    void clienteSalvoPeloProfileServiceDeveSerVisivelNoApplicationService() {
        String telefone = "5511911112222";

        customerProfileService.salvarNome(telefone, "Cliente Wiring");

        // Identidade é surrogate; casa pela chave lógica (tenant + telefone/nome),
        // não por um id derivado do telefone.
        List<Customer> todos = customerApplicationService.listarTodos(TestTenants.PILOT);

        assertTrue(todos.stream().anyMatch(c -> "Cliente Wiring".equals(c.getName().getFullName())),
                "O cliente salvo pelo CustomerProfileService deveria aparecer no "
                + "CustomerApplicationService — ambos compartilham o mesmo repositório e tenant");
    }
}
