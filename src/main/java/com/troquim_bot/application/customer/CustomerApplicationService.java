package com.troquim_bot.application.customer;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.repository.CustomerRepository;

import java.util.List;
import java.util.Optional;

/**
 * Application Service para gerenciar Customers.
 * 
 * Responsabilidades:
 * - Criar clientes
 * - Listar clientes
 * - Buscar clientes
 * - Atualizar clientes
 * - Inativar clientes
 */
@org.springframework.stereotype.Service
public class CustomerApplicationService {

    private final CustomerRepository customerRepository;

    /**
     * Construtor com injeção de dependência. Como é o único construtor,
     * o Spring injeta o CustomerRepository @Primary (JPA) automaticamente —
     * o mesmo repositório usado por CustomerProfileService.
     */
    public CustomerApplicationService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Cria um novo cliente.
     * 
     * @param name Nome completo do cliente
     * @param phone Telefone do cliente
     * @param notes Observações (opcional)
     * @return Customer criado com status ATIVO
     */
    public Customer criarCliente(String name, String phone, String notes) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        CustomerId id = CustomerId.generate();
        CustomerName customerName = CustomerName.of(name.trim());
        PhoneNumber phoneNumber = new PhoneNumber(phone.trim());

        Customer customer = new Customer(id, customerName, phoneNumber, notes);

        return customerRepository.save(customer);
    }

    /**
     * Busca cliente por ID.
     * 
     * @param id ID do cliente
     * @return Optional com o Customer se encontrado
     */
    public Optional<Customer> buscarPorId(CustomerId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(customerRepository.findById(id));
    }

    /**
     * Lista todos os clientes.
     * 
     * @return Lista de todos os customers
     */
    public List<Customer> listarTodos() {
        return customerRepository.findAll();
    }

    /**
     * Lista apenas clientes ativos.
     * 
     * @return Lista de customers ativos
     */
    public List<Customer> listarAtivos() {
        return customerRepository.findAll().stream()
            .filter(Customer::isAtivo)
            .toList();
    }

    /**
     * Atualiza o nome do cliente.
     * 
     * @param id ID do cliente
     * @param novoNome Novo nome
     * @return Customer atualizado
     */
    public Customer atualizarNome(CustomerId id, String novoNome) {
        Customer customer = getCustomerOrThrow(id);
        CustomerName customerName = CustomerName.of(novoNome.trim());
        customer.atualizarNome(customerName);
        return customerRepository.save(customer);
    }

    /**
     * Atualiza o telefone do cliente.
     * 
     * @param id ID do cliente
     * @param novoTelefone Novo telefone
     * @return Customer atualizado
     */
    public Customer atualizarTelefone(CustomerId id, String novoTelefone) {
        Customer customer = getCustomerOrThrow(id);
        PhoneNumber phoneNumber = new PhoneNumber(novoTelefone.trim());
        customer.atualizarTelefone(phoneNumber);
        return customerRepository.save(customer);
    }

    /**
     * Atualiza as observações do cliente.
     * 
     * @param id ID do cliente
     * @param novasObservacoes Novas observações
     * @return Customer atualizado
     */
    public Customer atualizarObservacoes(CustomerId id, String novasObservacoes) {
        Customer customer = getCustomerOrThrow(id);
        customer.atualizarObservacoes(novasObservacoes);
        return customerRepository.save(customer);
    }

    /**
     * Inativa um cliente.
     * 
     * @param id ID do cliente
     * @return Customer inativado
     */
    public Customer inativarCliente(CustomerId id) {
        Customer customer = getCustomerOrThrow(id);
        customer.inativar();
        return customerRepository.save(customer);
    }

    /**
     * Ativa um cliente.
     * 
     * @param id ID do cliente
     * @return Customer ativado
     */
    public Customer ativarCliente(CustomerId id) {
        Customer customer = getCustomerOrThrow(id);
        customer.ativar();
        return customerRepository.save(customer);
    }

    /**
     * Verifica se um cliente existe.
     * 
     * @param id ID do cliente
     * @return true se existe
     */
    public boolean existe(CustomerId id) {
        if (id == null) {
            return false;
        }
        return customerRepository.exists(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Customer getCustomerOrThrow(CustomerId id) {
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            throw new IllegalArgumentException("Cliente não encontrado");
        }
        return customer;
    }
}