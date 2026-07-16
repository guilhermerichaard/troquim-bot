package com.troquim_bot.application.customer;

import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que CustomerApplicationService (read-path de GET /customers) usa o
 * MESMO CustomerRepository que CustomerProfileService (write-path do fluxo de
 * conversa). Regressão do bug em que um construtor no-arg criava um
 * InMemoryCustomerRepository desconectado e /customers ficava sempre vazio.
 */
@SpringBootTest
class CustomerApplicationServiceWiringTest {

    @Autowired
    private CustomerProfileService customerProfileService;

    @Autowired
    private CustomerApplicationService customerApplicationService;

    @Test
    void clienteSalvoPeloProfileServiceDeveSerVisivelNoApplicationService() {
        String telefone = "5511911112222";

        customerProfileService.salvarNome(telefone, "Cliente Wiring");

        CustomerId esperado = CustomerId.fromPhone(telefone);
        List<Customer> todos = customerApplicationService.listarTodos();

        assertTrue(todos.stream().anyMatch(c -> c.getId().equals(esperado)),
                "O cliente salvo pelo CustomerProfileService deveria aparecer no "
                + "CustomerApplicationService — ambos devem compartilhar o mesmo repositório");
    }
}
