package com.troquim_bot.customer;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.repository.CustomerRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Serviço de perfil do cliente.
 * 
 * Customer é a única fonte da verdade do cliente.
 * CustomerProfileService usa CustomerRepository como camada de persistência.
 * 
 * Mantém o contrato público para compatibilidade com ConversationService
 * e ConversationContextResolver, que usam CustomerProfile como DTO.
 */
@Service
public class CustomerProfileService {

    private final CustomerRepository customerRepository;

    public CustomerProfileService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Optional<CustomerProfile> localizarPorTelefone(String numero) {
        CustomerId id = CustomerId.fromPhone(numero);
        Customer customer = customerRepository.findById(id);
        return Optional.ofNullable(customer)
                .map(c -> CustomerProfile.fromCustomer(c, numero));
    }

    public CustomerProfile buscarOuCriar(String numero) {
        CustomerId id = CustomerId.fromPhone(numero);
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            customer = criarCliente(numero);
        }
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile iniciarAtendimento(String numero) {
        CustomerId id = CustomerId.fromPhone(numero);
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            customer = criarCliente(numero);
        }
        customer.registrarAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile salvarNome(String numero, String nome) {
        if (nome == null || nome.isBlank()) {
            return buscarOuCriar(numero);
        }

        CustomerId id = CustomerId.fromPhone(numero);
        Customer customer = customerRepository.findById(id);
        CustomerName customerName = criarCustomerName(nome.trim());
        if (customer == null) {
            PhoneNumber phone = new PhoneNumber(numero);
            customer = new Customer(id, customerName, phone, null);
        } else {
            customer.atualizarNome(customerName);
        }
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    private CustomerName criarCustomerName(String nome) {
        try {
            return CustomerName.of(nome);
        } catch (IllegalArgumentException e) {
            // Se o nome não tiver sobrenome, usa "Sr" como sobrenome padrão
            return new CustomerName(nome, "Sr");
        }
    }

    public CustomerProfile atualizarNome(String numero, String nome) {
        return salvarNome(numero, nome);
    }

    public Optional<String> nomePreferido(CustomerProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }

        if (temValor(profile.getApelido())) {
            return Optional.of(profile.getApelido().trim());
        }

        if (temValor(profile.getNome()) && !isNomeGenerico(profile.getNome())) {
            return Optional.of(profile.getNome().trim());
        }

        return Optional.empty();
    }

    private boolean isNomeGenerico(String nome) {
        // Ignora nomes padrão atribuídos para clientes recém-criados
        return nome != null && nome.strip().toLowerCase().startsWith("cliente");
    }

    public Optional<String> nomePreferido(String numero) {
        return localizarPorTelefone(numero).flatMap(this::nomePreferido);
    }

    public CustomerProfile atualizarUltimoAtendimento(String numero) {
        CustomerId id = CustomerId.fromPhone(numero);
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            customer = criarCliente(numero);
        }
        customer.atualizarUltimoAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    private Customer criarCliente(String numero) {
        CustomerId id = CustomerId.fromPhone(numero);
        PhoneNumber phone = new PhoneNumber(numero);
        // Nome genérico que será ignorado pelo nomePreferido
        CustomerName name = new CustomerName("Cliente", phone.getDdd());
        Customer customer = new Customer(id, name, phone, null);
        return customerRepository.save(customer);
    }

    private boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}