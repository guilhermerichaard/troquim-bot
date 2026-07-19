package com.troquim_bot.application.customer;

import com.troquim_bot.business.BusinessId;
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
 * Isolamento por tenant: as operações de criação/listagem/busca-por-telefone
 * recebem um {@link BusinessId} explícito. O {@code CustomerId} é surrogate;
 * a identidade lógica do cliente é (BusinessId, phone E.164). Não há listagem
 * global.
 */
@org.springframework.stereotype.Service
public class CustomerApplicationService {

    private final CustomerRepository customerRepository;

    public CustomerApplicationService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Cria um novo cliente no tenant informado.
     *
     * O id é surrogate (gerado). O telefone é normalizado para E.164. Se já
     * existir um cliente com o mesmo (BusinessId, phoneE164), NÃO cria duplicata:
     * lança IllegalArgumentException (a unique constraint é a rede final no banco).
     */
    public Customer criarCliente(BusinessId businessId, String name, String phone, String notes) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        PhoneNumber phoneNumber = new PhoneNumber(phone.trim());

        if (customerRepository.findByBusinessAndPhone(businessId, phoneNumber).isPresent()) {
            throw new IllegalArgumentException(
                    "Já existe um cliente com este telefone neste negócio");
        }

        CustomerId id = CustomerId.generate();
        CustomerName customerName = CustomerName.of(name.trim());
        Customer customer = new Customer(id, businessId, customerName, phoneNumber, notes);

        return customerRepository.save(customer);
    }

    /**
     * Busca cliente pelo id surrogate (globalmente único).
     */
    public Optional<Customer> buscarPorId(CustomerId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(customerRepository.findById(id));
    }

    /**
     * Busca o cliente de um tenant pelo telefone (chave lógica).
     */
    public Optional<Customer> buscarPorTelefone(BusinessId businessId, PhoneNumber phone) {
        if (businessId == null || phone == null) {
            return Optional.empty();
        }
        return customerRepository.findByBusinessAndPhone(businessId, phone);
    }

    /**
     * Lista os clientes do tenant informado.
     */
    public List<Customer> listarTodos(BusinessId businessId) {
        return customerRepository.findByBusinessId(businessId);
    }

    /**
     * Lista apenas os clientes ativos do tenant informado.
     */
    public List<Customer> listarAtivos(BusinessId businessId) {
        return customerRepository.findByBusinessId(businessId).stream()
                .filter(Customer::isAtivo)
                .toList();
    }

    public Customer atualizarNome(CustomerId id, String novoNome) {
        Customer customer = getCustomerOrThrow(id);
        CustomerName customerName = CustomerName.of(novoNome.trim());
        customer.atualizarNome(customerName);
        return customerRepository.save(customer);
    }

    public Customer atualizarTelefone(CustomerId id, String novoTelefone) {
        Customer customer = getCustomerOrThrow(id);
        PhoneNumber phoneNumber = new PhoneNumber(novoTelefone.trim());
        customer.atualizarTelefone(phoneNumber);
        return customerRepository.save(customer);
    }

    public Customer atualizarObservacoes(CustomerId id, String novasObservacoes) {
        Customer customer = getCustomerOrThrow(id);
        customer.atualizarObservacoes(novasObservacoes);
        return customerRepository.save(customer);
    }

    public Customer inativarCliente(CustomerId id) {
        Customer customer = getCustomerOrThrow(id);
        customer.inativar();
        return customerRepository.save(customer);
    }

    public Customer ativarCliente(CustomerId id) {
        Customer customer = getCustomerOrThrow(id);
        customer.ativar();
        return customerRepository.save(customer);
    }

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
