package com.troquim_bot.customer;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;

import java.time.LocalDateTime;

/**
 * Aggregate Root que representa um cliente do Troquim.
 * 
 * Responsabilidades:
 * - Gerenciar informações do cliente (nome, telefone, observações)
 * - Controlar o ciclo de vida do cliente (ATIVO, INATIVO)
 * - Proteger invariants de negócio
 */
public class Customer {

    private final CustomerId id;
    private CustomerName name;
    private PhoneNumber phone;
    private String notes;
    private CustomerStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Customer.
     * Inicia com status ATIVO.
     */
    public Customer(CustomerId id, CustomerName name, PhoneNumber phone, String notes) {
        if (id == null) {
            throw new IllegalArgumentException("CustomerId é obrigatório");
        }
        if (name == null) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (phone == null) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        this.id = id;
        this.name = name;
        this.phone = phone;
        this.notes = notes != null ? notes.trim() : null;
        this.status = CustomerStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Customer existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Customer(CustomerId id, CustomerName name, PhoneNumber phone, String notes,
                     CustomerStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("CustomerId é obrigatório");
        }
        if (name == null) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.name = name;
        this.phone = phone;
        this.notes = notes != null ? notes.trim() : null;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public CustomerId getId() {
        return id;
    }

    public CustomerName getName() {
        return name;
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

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}