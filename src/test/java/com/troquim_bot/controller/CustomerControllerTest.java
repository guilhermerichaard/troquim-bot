package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateCustomerRequest;
import com.troquim_bot.controller.dto.UpdateCustomerRequest;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerControllerTest {

    private MockMvc mockMvc;
    private CustomerApplicationService customerApplicationService;
    private InMemoryCustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository = new InMemoryCustomerRepository();
        customerApplicationService = new CustomerApplicationService(customerRepository);
        CustomerController customerController = new CustomerController(customerApplicationService, TestTenants.pilot());
        mockMvc = MockMvcBuilders.standaloneSetup(customerController).build();
    }

    // ==================== GET /customers ====================

    @Test
    void deveRetornar200QuandoListarTodos() throws Exception {
        // Cria alguns clientes
        customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", "Cliente VIP");
        customerApplicationService.criarCliente(TestTenants.PILOT,"Maria Souza", "+5511988888888", null);

        // Testa GET /customers
        mockMvc.perform(get("/customers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItems("João Silva", "Maria Souza")));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemClientes() throws Exception {
        // Testa GET /customers sem clientes
        mockMvc.perform(get("/customers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getCustomersRetornaSomenteDoTenantCorrente() throws Exception {
        // O controller resolve o tenant como PILOT (TestTenants.pilot()).
        customerApplicationService.criarCliente(TestTenants.PILOT, "Ana Pilot", "5511900000010", null);
        // Cliente de OUTRO tenant não pode aparecer no GET /customers.
        customerApplicationService.criarCliente(TestTenants.OUTRO, "Bruno Outro", "5511900000011", null);

        mockMvc.perform(get("/customers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Ana Pilot"));
    }

    // ==================== GET /customers/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarPorIdExistente() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", "Cliente VIP");
        String customerId = customer.getId().getValue().toString();

        // Testa GET /customers/{id}
        mockMvc.perform(get("/customers/" + customerId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(customerId))
            .andExpect(jsonPath("$.name").value("João Silva"))
            .andExpect(jsonPath("$.phone").value("+5511999999999"))
            .andExpect(jsonPath("$.notes").value("Cliente VIP"))
            .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void deveRetornar404QuandoBuscarPorIdInexistente() throws Exception {
        // Gera um UUID que não existe
        String nonExistentId = UUID.randomUUID().toString();

        // Testa GET /customers/{id} com ID inexistente
        mockMvc.perform(get("/customers/" + nonExistentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoIdInvalido() throws Exception {
        // Testa GET /customers/{id} com ID inválido
        mockMvc.perform(get("/customers/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /customers ====================

    @Test
    void deveCriarClienteERetornar201() throws Exception {
        // Prepara request
        String requestBody = "{\"name\":\"João Silva\",\"phone\":\"+5511999999999\",\"notes\":\"Cliente VIP\"}";

        // Testa POST /customers
        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("João Silva"))
            .andExpect(jsonPath("$.phone").value("+5511999999999"))
            .andExpect(jsonPath("$.notes").value("Cliente VIP"))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveCriarClienteSemObservacoes() throws Exception {
        // Prepara request sem observações
        String requestBody = "{\"name\":\"Maria Souza\",\"phone\":\"+5511988888888\"}";

        // Testa POST /customers
        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Maria Souza"))
            .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void deveRetornar400QuandoNomeVazio() throws Exception {
        // Prepara request com nome vazio
        String requestBody = "{\"name\":\"\",\"phone\":\"+5511999999999\"}";

        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoTelefoneVazio() throws Exception {
        // Prepara request com telefone vazio
        String requestBody = "{\"name\":\"João Silva\",\"phone\":\"\"}";

        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        // Testa POST /customers com request null
        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /customers/{id} ====================

    @Test
    void deveAtualizarNome() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", "Original");
        String customerId = customer.getId().getValue().toString();

        // Atualiza apenas o nome
        String requestBody = "{\"name\":\"João Santos\"}";

        mockMvc.perform(put("/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("João Santos"))
            .andExpect(jsonPath("$.phone").value("+5511999999999")) // Não alterado
            .andExpect(jsonPath("$.notes").value("Original")); // Não alterado
    }

    @Test
    void deveAtualizarTelefone() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", null);
        String customerId = customer.getId().getValue().toString();

        // Atualiza apenas o telefone
        String requestBody = "{\"phone\":\"+5511988888888\"}";

        mockMvc.perform(put("/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("João Silva")) // Não alterado
            .andExpect(jsonPath("$.phone").value("+5511988888888"));
    }

    @Test
    void deveAtualizarObservacoes() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", "Original");
        String customerId = customer.getId().getValue().toString();

        // Atualiza apenas as observações
        String requestBody = "{\"notes\":\"Atualizado\"}";

        mockMvc.perform(put("/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value("Atualizado"));
    }

    @Test
    void deveAtualizarTodosOsCampos() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", "Original");
        String customerId = customer.getId().getValue().toString();

        // Atualiza todos os campos
        String requestBody = "{\"name\":\"João Santos\",\"phone\":\"+5511988888888\",\"notes\":\"Atualizado\"}";

        mockMvc.perform(put("/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("João Santos"))
            .andExpect(jsonPath("$.phone").value("+5511988888888"))
            .andExpect(jsonPath("$.notes").value("Atualizado"));
    }

    @Test
    void deveRetornar400QuandoAtualizarClienteInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();
        String requestBody = "{\"name\":\"Novo Nome\"}";

        mockMvc.perform(put("/customers/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /customers/{id} ====================

    @Test
    void deveInativarClienteERetornar204() throws Exception {
        // Cria um cliente
        Customer customer = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", null);
        String customerId = customer.getId().getValue().toString();

        // Verifica que está ativo
        assertTrue(customerApplicationService.buscarPorId(customer.getId()).orElseThrow().isAtivo());

        // Testa DELETE /customers/{id}
        mockMvc.perform(delete("/customers/" + customerId))
            .andExpect(status().isNoContent());

        // Verifica que foi inativado (soft delete)
        Customer customerInativado = customerApplicationService.buscarPorId(customer.getId()).orElseThrow();
        assertFalse(customerInativado.isAtivo());
        assertEquals(CustomerStatus.INATIVO, customerInativado.getStatus());
    }

    @Test
    void deveRetornar400QuandoDeletarClienteInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        // Testa DELETE /customers/{id} com ID inexistente
        mockMvc.perform(delete("/customers/" + nonExistentId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoIdInvalidoNoDelete() throws Exception {
        // Testa DELETE /customers/{id} com ID inválido
        mockMvc.perform(delete("/customers/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void clienteInativadoNaoApareceNaListaDeAtivos() throws Exception {
        // Cria dois clientes
        Customer customer1 = customerApplicationService.criarCliente(TestTenants.PILOT,"João Silva", "+5511999999999", null);
        Customer customer2 = customerApplicationService.criarCliente(TestTenants.PILOT,"Maria Souza", "+5511988888888", null);

        // Inativa o segundo cliente
        customerApplicationService.inativarCliente(customer2.getId());

        // Verifica GET /customers retorna ambos
        mockMvc.perform(get("/customers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }
}