package com.troquim_bot.customer;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;

import java.time.LocalDateTime;

/**
 * Aggregate Root que representa um cliente do Troquim.
 * 
 * Fonte única da verdade do cliente.
 * Responsabilidades:
 * - Gerenciar informações do cliente (nome, apelido, telefone, observações)
 * - Controlar o ciclo de vida do cliente (ATIVO, INATIVO)
 * - Rastrear total de atendimentos e último atendimento
 * - Proteger invariants de negócio
 */
public class Customer {

    private final CustomerId id;
    private final BusinessId businessId;
    private CustomerName name;
    private String apelido;
    private PhoneNumber phone;
    private String notes;
    private CustomerStatus status;
    private int totalAtendimentos;
    private LocalDateTime ultimoAtendimento;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Customer.
     * Inicia com status ATIVO.
     */
    public Customer(CustomerId id, BusinessId businessId, CustomerName name, PhoneNumber phone, String notes) {
        if (id == null) {
            throw new IllegalArgumentException("CustomerId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (name == null) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (phone == null) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.name = name;
        this.phone = phone;
        this.notes = notes != null ? notes.trim() : null;
        this.status = CustomerStatus.ATIVO;
        this.totalAtendimentos = 0;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Customer existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Customer(CustomerId id, BusinessId businessId, CustomerName name, PhoneNumber phone, String notes,
                     CustomerStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this(id, businessId, name, phone, notes, null, status, 0, null, criadoEm, atualizadoEm);
    }

    /**
     * Construtor completo para reconstituição de Customer existente.
     * Usado apenas pela infraestrutura.
     */
    public Customer(CustomerId id, BusinessId businessId, CustomerName name, PhoneNumber phone, String notes,
                     CustomerStatus status, int totalAtendimentos, LocalDateTime ultimoAtendimento,
                     LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this(id, businessId, name, phone, notes, null, status, totalAtendimentos, ultimoAtendimento, criadoEm, atualizadoEm);
    }

    /**
     * Construtor completo para reconstituição de Customer existente, incluindo apelido.
     * Usado apenas pela infraestrutura.
     */
    public Customer(CustomerId id, BusinessId businessId, CustomerName name, PhoneNumber phone, String notes,
                     String apelido, CustomerStatus status, int totalAtendimentos, LocalDateTime ultimoAtendimento,
                     LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("CustomerId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (name == null) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.name = name;
        this.phone = phone;
        this.notes = notes != null ? notes.trim() : null;
        this.apelido = apelido;
        this.status = status;
        this.totalAtendimentos = totalAtendimentos;
        this.ultimoAtendimento = ultimoAtendimento;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public CustomerId getId() {
        return id;
    }

    public BusinessId getBusinessId() {
        return businessId;
    }

    public CustomerName getName() {
        return name;
    }

    public String getApelido() {
        return apelido;
    }

    public PhoneNumber getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public int getTotalAtendimentos() {
        return totalAtendimentos;
    }

    public LocalDateTime getUltimoAtendimento() {
        return ultimoAtendimento;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    // ==================== MÉTODOS DE NEGÓCIO ====================

    /**
     * Verifica se o Customer está ativo.
     */
    public boolean isAtivo() {
        return status == CustomerStatus.ATIVO;
    }

    /**
     * Atualiza o nome do cliente.
     */
    public void atualizarNome(CustomerName name) {
        if (name == null) {
            throw new IllegalArgumentException("Nome do cliente não pode ser nulo");
        }
        this.name = name;
        tocar();
    }

    /**
     * Define o apelido/nome preferido do cliente.
     */
    public void definirApelido(String apelido) {
        this.apelido = apelido != null ? apelido.trim() : null;
        tocar();
    }

    /**
     * Atualiza o telefone do cliente.
     */
    public void atualizarTelefone(PhoneNumber phone) {
        if (phone == null) {
            throw new IllegalArgumentException("Telefone não pode ser nulo");
        }
        this.phone = phone;
        tocar();
    }

    /**
     * Atualiza as observações do cliente.
     */
    public void atualizarObservacoes(String notes) {
        this.notes = notes != null ? notes.trim() : null;
        tocar();
    }

    /**
     * Ativa o Customer (transição para ATIVO).
     */
    public void ativar() {
        if (status == CustomerStatus.INATIVO) {
            this.status = CustomerStatus.ATIVO;
            tocar();
        }
    }

    /**
     * Inativa o Customer (transição para INATIVO).
     */
    public void inativar() {
        if (status == CustomerStatus.ATIVO) {
            this.status = CustomerStatus.INATIVO;
            tocar();
        }
    }

    /**
     * Registra um novo atendimento para este cliente.
     * Incrementa o contador e atualiza a data do último atendimento.
     */
    public void registrarAtendimento() {
        this.totalAtendimentos++;
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    /**
     * Atualiza apenas a data do último atendimento (sem incrementar contador).
     */
    public void atualizarUltimoAtendimento() {
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}