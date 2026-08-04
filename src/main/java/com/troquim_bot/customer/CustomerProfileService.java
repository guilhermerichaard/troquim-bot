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
 * Customer é a única fonte da verdade do cliente. A identidade do Customer é surrogate; o
 * resolve-or-create é por (BusinessId, phone E.164), NUNCA por id derivado do telefone.
 *
 * DUAS FAMÍLIAS DE ASSINATURA:
 * <ul>
 *   <li>EXPLÍCITAS ({@code BusinessId} como primeiro parâmetro) — o caminho tipado de
 *       booking as usa. Nunca consultam {@link TenantProvider}: todo método interno de
 *       resolução recebe o tenant por argumento;</li>
 *   <li>LEGADAS (baseadas só em {@code numero}) — preservadas para a camada de Conversation.
 *       Resolvem o {@link TenantProvider} NA BORDA (uma vez, no início do método) e delegam
 *       à versão explícita — nenhuma lógica de criação, nome ou telefone é duplicada.</li>
 * </ul>
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
        return resolver(tenantProvider.currentBusinessId(), numero)
                .map(c -> CustomerProfile.fromCustomer(c, numero));
    }

    public CustomerProfile buscarOuCriar(String numero) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        Customer customer = resolver(businessId, numero).orElseGet(() -> criarCliente(businessId, numero));
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile iniciarAtendimento(String numero) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        Customer customer = resolver(businessId, numero).orElseGet(() -> criarCliente(businessId, numero));
        customer.registrarAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    public CustomerProfile salvarNome(String numero, String nome) {
        if (nome == null || nome.isBlank()) {
            return buscarOuCriar(numero);
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        CustomerName customerName = criarCustomerName(nome.trim());
        Customer customer = resolver(businessId, numero).orElse(null);
        if (customer == null) {
            customer = new Customer(CustomerId.generate(), businessId, customerName, new PhoneNumber(numero), null);
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
     * Resolve o Customer do {@code businessId} EXPLÍCITO pela chave lógica (BusinessId, phone
     * E.164), ou CONSTRÓI um novo (ainda não persistido) com o nome informado. Não persiste.
     *
     * Caminho usado pelo booking tipado: nunca consulta {@link TenantProvider} — o tenant vem
     * sempre do {@code BusinessId} da {@code BookingCommandKey}, nunca de contexto implícito.
     *
     * O {@code CustomerId} do resultado é o id oficial surrogate — já persistido se o
     * cliente existir, ou o que será persistido por {@link #persistir(Customer)} caso o
     * fluxo de confirmação conclua sem conflito. Isto permite ao caso de uso reservar a
     * identidade oficial antes da checagem de conflito sem deixar Customer órfão quando o
     * horário está ocupado.
     */
    public Customer resolverOuConstruir(BusinessId businessId, String numero, String nome) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para resolver o cliente");
        }
        Customer existente = resolver(businessId, numero).orElse(null);
        if (existente != null) {
            if (temValor(nome)) {
                existente.atualizarNome(criarCustomerName(nome.trim()));
            }
            return existente;
        }
        CustomerName name = temValor(nome)
                ? criarCustomerName(nome.trim())
                : nomeGenerico(numero);
        return new Customer(CustomerId.generate(), businessId, name, new PhoneNumber(numero), null);
    }

    /** LEGADO — resolve no {@link TenantProvider} corrente e delega. Uso: Conversation. */
    public Customer resolverOuConstruir(String numero, String nome) {
        return resolverOuConstruir(tenantProvider.currentBusinessId(), numero, nome);
    }

    /**
     * Persiste (cria ou atualiza) o Customer resolvido/construído. Chamado uma única vez,
     * após o sucesso do agendamento.
     */
    public Customer persistir(Customer customer) {
        return customerRepository.save(customer);
    }

    /**
     * Resolve-or-create do Customer do {@code businessId} EXPLÍCITO e devolve o
     * {@code CustomerId} oficial surrogate (persistindo o cliente se ainda não existir). É
     * idempotente: mesmo (BusinessId, phone) → mesmo id. Nunca consulta {@link TenantProvider}.
     */
    public CustomerId resolverIdOficial(BusinessId businessId, String numero) {
        return persistir(resolverOuConstruir(businessId, numero, null)).getId();
    }

    /** LEGADO — resolve no {@link TenantProvider} corrente e delega. Uso: Conversation. */
    public CustomerId resolverIdOficial(String numero) {
        return resolverIdOficial(tenantProvider.currentBusinessId(), numero);
    }

    /**
     * Localiza o {@code CustomerId} oficial de um telefone no {@code businessId} EXPLÍCITO,
     * SEM criar Customer. Nunca consulta {@link TenantProvider}.
     */
    public Optional<CustomerId> localizarIdOficial(BusinessId businessId, String numero) {
        return resolver(businessId, numero).map(Customer::getId);
    }

    /**
     * LEGADO — resolve no {@link TenantProvider} corrente e delega. Usado nos caminhos de
     * leitura/consulta/cancelamento da Conversation: se o cliente não existe, também não há
     * agendamentos a listar. A Conversation nunca cria nem deriva id.
     */
    public Optional<CustomerId> localizarIdOficial(String numero) {
        return localizarIdOficial(tenantProvider.currentBusinessId(), numero);
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
        BusinessId businessId = tenantProvider.currentBusinessId();
        Customer customer = resolver(businessId, numero).orElseGet(() -> criarCliente(businessId, numero));
        customer.atualizarUltimoAtendimento();
        customerRepository.save(customer);
        return CustomerProfile.fromCustomer(customer, numero);
    }

    /** Resolve o Customer do {@code businessId} informado pelo telefone (chave lógica). */
    private Optional<Customer> resolver(BusinessId businessId, String numero) {
        return customerRepository.findByBusinessAndPhone(businessId, new PhoneNumber(numero));
    }

    private Customer criarCliente(BusinessId businessId, String numero) {
        PhoneNumber phone = new PhoneNumber(numero);
        Customer customer = new Customer(CustomerId.generate(), businessId, nomeGenerico(numero), phone, null);
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
