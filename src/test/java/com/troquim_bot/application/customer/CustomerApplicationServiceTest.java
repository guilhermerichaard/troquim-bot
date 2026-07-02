package com.troquim_bot.application.customer;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerStatus;
import com.troquim_bot.repository.InMemoryCustomerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerApplicationServiceTest {

    private CustomerApplicationService customerApplicationService;
    private InMemoryCustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository = new InMemoryCustomerRepository();
        customerApplicationService = new CustomerApplicationService(customerRepository);
    }

    // ==================== criarCliente ====================

    @Test
    void deveCriarClienteComSucesso() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", "Cliente VIP");

        assertNotNull(customer);
        assertNotNull(customer.getId());
        assertEquals("João Silva", customer.getName().getFullName());
        assertEquals("+5511999999999", customer.getPhone().getValue());
        assertEquals("Cliente VIP", customer.getNotes());
        assertEquals(CustomerStatus.ATIVO, customer.getStatus());
        assertTrue(customer.isAtivo());
        assertNotNull(customer.getCriadoEm());
        assertNotNull(customer.getAtualizadoEm());
    }

    @Test
    void deveCriarClienteSemObservacoes() {
        Customer customer = customerApplicationService.criarCliente("Maria Souza", "+5511988888888", null);

        assertNotNull(customer);
        assertNull(customer.getNotes());
        assertEquals("Maria Souza", customer.getName().getFullName());
    }

    @Test
    void deveLancarExcecaoQuandoNomeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.criarCliente(null, "+5511999999999", "teste"));
    }

    @Test
    void deveLancarExcecaoQuandoNomeVazio() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.criarCliente("", "+5511999999999", "teste"));
    }

    @Test
    void deveLancarExcecaoQuandoTelefoneNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.criarCliente("João Silva", null, "teste"));
    }

    @Test
    void deveLancarExcecaoQuandoTelefoneVazio() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.criarCliente("João Silva", "", "teste"));
    }

    // ==================== buscarPorId ====================

    @Test
    void deveBuscarClientePorId() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);
        CustomerId id = customer.getId();

        Optional<Customer> encontrado = customerApplicationService.buscarPorId(id);

        assertTrue(encontrado.isPresent());
        assertEquals(customer.getId(), encontrado.get().getId());
        assertEquals("João Silva", encontrado.get().getName().getFullName());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        Optional<Customer> encontrado = customerApplicationService.buscarPorId(CustomerId.generate());

        assertFalse(encontrado.isPresent());
    }

    @Test
    void deveRetornarVazioQuandoIdNulo() {
        Optional<Customer> encontrado = customerApplicationService.buscarPorId(null);

        assertFalse(encontrado.isPresent());
    }

    // ==================== listarTodos ====================

    @Test
    void deveListarTodosOsClientes() {
        customerApplicationService.criarCliente("João Silva", "+5511999999999", null);
        customerApplicationService.criarCliente("Maria Souza", "+5511988888888", null);
        customerApplicationService.criarCliente("Pedro Santos", "+5511977777777", null);

        List<Customer> customers = customerApplicationService.listarTodos();

        assertEquals(3, customers.size());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemClientes() {
        List<Customer> customers = customerApplicationService.listarTodos();

        assertTrue(customers.isEmpty());
    }

    // ==================== listarAtivos ====================

    @Test
    void deveListarApenasClientesAtivos() {
        Customer ativo1 = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);
        Customer ativo2 = customerApplicationService.criarCliente("Maria Souza", "+5511988888888", null);
        Customer inativo = customerApplicationService.criarCliente("Pedro Santos", "+5511977777777", null);

        customerApplicationService.inativarCliente(inativo.getId());

        List<Customer> ativos = customerApplicationService.listarAtivos();

        assertEquals(2, ativos.size());
        assertTrue(ativos.stream().allMatch(Customer::isAtivo));
    }

    // ==================== atualizarNome ====================

    @Test
    void deveAtualizarNome() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);

        Customer atualizado = customerApplicationService.atualizarNome(customer.getId(), "João Santos");

        assertEquals("João Santos", atualizado.getName().getFullName());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarNomeDeClienteInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.atualizarNome(CustomerId.generate(), "João Santos"));
    }

    // ==================== atualizarTelefone ====================

    @Test
    void deveAtualizarTelefone() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);

        Customer atualizado = customerApplicationService.atualizarTelefone(customer.getId(), "+5511988888888");

        assertEquals("+5511988888888", atualizado.getPhone().getValue());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarTelefoneDeClienteInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.atualizarTelefone(CustomerId.generate(), "+5511988888888"));
    }

    // ==================== atualizarObservacoes ====================

    @Test
    void deveAtualizarObservacoes() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", "Original");

        Customer atualizado = customerApplicationService.atualizarObservacoes(customer.getId(), "Atualizado");

        assertEquals("Atualizado", atualizado.getNotes());
    }

    @Test
    void deveLimparObservacoes() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", "Original");

        Customer atualizado = customerApplicationService.atualizarObservacoes(customer.getId(), null);

        assertNull(atualizado.getNotes());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarObservacoesDeClienteInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.atualizarObservacoes(CustomerId.generate(), "teste"));
    }

    // ==================== inativarCliente ====================

    @Test
    void deveInativarCliente() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);

        Customer inativado = customerApplicationService.inativarCliente(customer.getId());

        assertEquals(CustomerStatus.INATIVO, inativado.getStatus());
        assertFalse(inativado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoInativarClienteInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.inativarCliente(CustomerId.generate()));
    }

    // ==================== ativarCliente ====================

    @Test
    void deveAtivarCliente() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);
        customerApplicationService.inativarCliente(customer.getId());

        Customer ativado = customerApplicationService.ativarCliente(customer.getId());

        assertEquals(CustomerStatus.ATIVO, ativado.getStatus());
        assertTrue(ativado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoAtivarClienteInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            customerApplicationService.ativarCliente(CustomerId.generate()));
    }

    // ==================== existe ====================

    @Test
    void deveRetornarTrueQuandoClienteExiste() {
        Customer customer = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);

        assertTrue(customerApplicationService.existe(customer.getId()));
    }

    @Test
    void deveRetornarFalseQuandoClienteNaoExiste() {
        assertFalse(customerApplicationService.existe(CustomerId.generate()));
    }

    @Test
    void deveRetornarFalseQuandoIdNulo() {
        assertFalse(customerApplicationService.existe(null));
    }

    // ==================== Cliente inativado não aparece na lista de ativos ====================

    @Test
    void clienteInativadoNaoApareceNaListaDeAtivos() {
        Customer ativo = customerApplicationService.criarCliente("João Silva", "+5511999999999", null);
        Customer inativo = customerApplicationService.criarCliente("Maria Souza", "+5511988888888", null);

        customerApplicationService.inativarCliente(inativo.getId());

        List<Customer> ativos = customerApplicationService.listarAtivos();
        assertEquals(1, ativos.size());
        assertEquals("João Silva", ativos.get(0).getName().getFullName());
    }
}