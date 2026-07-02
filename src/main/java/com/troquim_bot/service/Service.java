package com.troquim_bot.service;

import com.troquim_bot.common.valueobject.Money;

import java.time.LocalDateTime;

/**
 * Aggregate Root que representa um serviço oferecido por um Business.
 * 
 * Responsabilidades:
 * - Gerenciar informações do serviço (nome, descrição, duração, preço)
 * - Controlar o ciclo de vida do serviço (ATIVO, INATIVO)
 * - Proteger invariants de negócio
 */
public class Service {

    private final ServiceId id;
    private String nome;
    private String descricao;
    private ServiceDuration duracao;
    private Money preco;
    private ServiceStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Service.
     * Inicia com status ATIVO.
     */
    public Service(ServiceId id, String nome, String descricao, ServiceDuration duracao, Money preco) {
        if (id == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (duracao == null) {
            throw new IllegalArgumentException("Duração é obrigatória");
        }
        if (preco == null) {
            throw new IllegalArgumentException("Preço é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.descricao = descricao != null ? descricao.trim() : null;
        this.duracao = duracao;
        this.preco = preco;
        this.status = ServiceStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Service existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Service(ServiceId id, String nome, String descricao, ServiceDuration duracao, Money preco,
                   ServiceStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.descricao = descricao != null ? descricao.trim() : null;
        this.duracao = duracao;
        this.preco = preco;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public ServiceId getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public ServiceDuration getDuracao() {
        return duracao;
    }

    public Money getPreco() {
        return preco;
    }

    public ServiceStatus getStatus() {
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
     * Verifica se o serviço está ativo e pode ser agendado.
     */
    public boolean isAtivo() {
        return status == ServiceStatus.ATIVO;
    }

    /**
     * Verifica se o serviço pode ser agendado.
     */
    public boolean podeSerAgendado() {
        return status == ServiceStatus.ATIVO;
    }

    /**
     * Atualiza o nome do serviço.
     */
    public void atualizarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço não pode ser vazio");
        }
        this.nome = nome.trim();
        tocar();
    }

    /**
     * Atualiza a descrição do serviço.
     */
    public void atualizarDescricao(String descricao) {
        this.descricao = descricao != null ? descricao.trim() : null;
        tocar();
    }

    /**
     * Atualiza a duração do serviço.
     */
    public void atualizarDuracao(ServiceDuration duracao) {
        if (duracao == null) {
            throw new IllegalArgumentException("Duração não pode ser nula");
        }
        this.duracao = duracao;
        tocar();
    }

    /**
     * Atualiza o preço do serviço.
     */
    public void atualizarPreco(Money preco) {
        if (preco == null) {
            throw new IllegalArgumentException("Preço não pode ser nulo");
        }
        this.preco = preco;
        tocar();
    }

    /**
     * Ativa o serviço (transição para ATIVO).
     */
    public void ativar() {
        this.status = ServiceStatus.ATIVO;
        tocar();
    }

    /**
     * Desativa o serviço (transição para INATIVO).
     */
    public void desativar() {
        this.status = ServiceStatus.INATIVO;
        tocar();
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}