package com.troquim_bot.customer;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.repository.CustomerRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Serviço de perfil do cliente.
 *
 * Customer é a única fonte da verdade do cliente. Este é o write-path usado pelo
 * fluxo de conversa/booking. A identidade do Customer é surrogate; o
 * resolve-or-create é por (BusinessId, phone E.164), NUNCA por id derivado do
 * telefone. As assinaturas públicas (baseadas em {@code numero}) são preservadas
 * para não alterar a camada de Conversation — o tenant é resolvido internamente
 * pelo {@link TenantProvider} centralizado.
 */
@Service
public class CustomerProfileService {

    private final CustomerRepository customerRepository;
    private final TenantProvider tenantProvider;

    public CustomerProfileService(CustomerRepository customerRepository, TenantProvider tenantProvider) {
        this.customerRepository = customerRepository;
        this.tenantProvider = tenantProvider;
    }

    public Optional<CustomerProfile> localizarPorTelefone(String numero) {
        return resolver(numero)
                .map(c -> CustomerProfile.fromCustomer(c, numero));
    }

    public CustomerProfile buscarOuCriar(String numero) {
        Customer customer = resolver(numero).orElseGet(() -> criarCliente(numero));
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile iniciarAtendimento(String numero) {
        Customer customer = resolver(numero).orElseGet(() -> criarCliente(numero));
        customer.registrarAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile salvarNome(String numero, String nome) {
        if (nome == null || nome.isBlank()) {
            return buscarOuCriar(numero);
        }

        CustomerName customerName = criarCustomerName(nome.trim());
        Customer customer = resolver(numero).orElse(null);
        if (customer == null) {
            customer = new Customer(CustomerId.generate(), tenantProvider.currentBusinessId(),
                    customerName, new PhoneNumber(numero), null);
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
        Customer customer = resolver(numero).orElseGet(() -> criarCliente(numero));
        customer.atualizarUltimoAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    /**
     * Resolve o Customer do tenant corrente pelo telefone (chave lógica).
     */
    private Optional<Customer> resolver(String numero) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        return customerRepository.findByBusinessAndPhone(businessId, new PhoneNumber(numero));
    }

    private Customer criarCliente(String numero) {
        PhoneNumber phone = new PhoneNumber(numero);
        // Nome genérico que será ignorado pelo nomePreferido
        CustomerName name = new CustomerName("Cliente", phone.getDdd());
        Customer customer = new Customer(CustomerId.generate(), tenantProvider.currentBusinessId(),
                name, phone, null);
        return customerRepository.save(customer);
    }

    private boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
