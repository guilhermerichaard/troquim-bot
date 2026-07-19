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

    // ==================== IDENTIDADE OFICIAL (ARCHITECTURE_V2_1 §C7/§C8) ====================
    //
    // Autoridade única de identidade do cliente. O CustomerId oficial é o surrogate
    // persistido do agregado Customer, resolvido por (BusinessId, phone E.164). Appointment,
    // Reservation e Conversation recebem SEMPRE este id — nunca CustomerId.fromPhone.

    /**
     * Resolve o Customer do tenant corrente pela chave lógica (BusinessId, phone E.164),
     * ou CONSTRÓI um novo (ainda não persistido) com o nome informado. Não persiste.
     *
     * O {@code CustomerId} do resultado é o id oficial surrogate — já persistido se o
     * cliente existir, ou o que será persistido por {@link #persistir(Customer)} caso o
     * fluxo de confirmação conclua sem conflito. Isto permite ao caso de uso reservar a
     * identidade oficial antes da checagem de conflito sem deixar Customer órfão quando o
     * horário está ocupado.
     */
    public Customer resolverOuConstruir(String numero, String nome) {
        Customer existente = resolver(numero).orElse(null);
        if (existente != null) {
            if (temValor(nome)) {
                existente.atualizarNome(criarCustomerName(nome.trim()));
            }
            return existente;
        }
        CustomerName name = temValor(nome)
                ? criarCustomerName(nome.trim())
                : nomeGenerico(numero);
        return new Customer(CustomerId.generate(), tenantProvider.currentBusinessId(),
                name, new PhoneNumber(numero), null);
    }

    /**
     * Persiste (cria ou atualiza) o Customer resolvido/construído. Chamado uma única vez,
     * após o sucesso do agendamento.
     */
    public Customer persistir(Customer customer) {
        return customerRepository.save(customer);
    }

    /**
     * Resolve-or-create do Customer do tenant corrente e devolve o {@code CustomerId}
     * oficial surrogate (persistindo o cliente se ainda não existir). É idempotente:
     * mesmo (BusinessId, phone) → mesmo id.
     */
    public CustomerId resolverIdOficial(String numero) {
        return persistir(resolverOuConstruir(numero, null)).getId();
    }

    /**
     * Localiza o {@code CustomerId} oficial de um telefone SEM criar Customer. Usado nos
     * caminhos de leitura/consulta/cancelamento da Conversation: se o cliente não existe,
     * também não há agendamentos a listar. A Conversation nunca cria nem deriva id.
     */
    public Optional<CustomerId> localizarIdOficial(String numero) {
        return resolver(numero).map(Customer::getId);
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
        Customer customer = new Customer(CustomerId.generate(), tenantProvider.currentBusinessId(),
                nomeGenerico(numero), phone, null);
        return customerRepository.save(customer);
    }

    /** Nome genérico (ignorado por {@link #nomePreferido(CustomerProfile)}) para clientes sem nome. */
    private CustomerName nomeGenerico(String numero) {
        return new CustomerName("Cliente", new PhoneNumber(numero).getDdd());
    }

    private boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
