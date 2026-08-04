package com.troquim_bot.business;

import java.time.LocalDateTime;

/**
 * Aggregate Root que representa a IDENTIDADE de um negócio cliente do Troquim.
 *
 * Responsabilidades:
 * - Guardar nome, contato e status do negócio
 * - Controlar o ciclo de vida do negócio (TRIAL, ATIVO, INATIVO, SUSPENSO, DELETADO)
 * - Ser a raiz de referência para todos os Aggregates tenant-scoped
 *
 * NÃO é autoridade sobre calendário: expediente vive em {@link BusinessCalendar}, um
 * agregado próprio. Um Business que também carregasse BusinessHours criaria DUAS fontes de
 * verdade assim que ambos fossem persistidos — a mesma semana lida de dois lugares que podem
 * divergir. Por isso Business não sabe nada de horário de funcionamento.
 */
public class Business {

    private final BusinessId id;
    private String nome;
    private String telefone;
    private String endereco;
    private BusinessStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Business. Inicia com status TRIAL.
     *
     * Contato é OPCIONAL: durante onboarding o dono pode não ter informado telefone nem
     * endereço ainda. Inventar um valor para satisfazer o construtor esconderia essa
     * ausência real atrás de um dado falso.
     */
    public Business(BusinessId id, String nome, String telefone, String endereco) {
        if (id == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do negócio é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.telefone = telefone != null ? telefone.trim() : null;
        this.endereco = endereco != null ? endereco.trim() : null;
        this.status = BusinessStatus.TRIAL;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Business existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Business(BusinessId id, String nome, String telefone, String endereco,
                    BusinessStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do negócio é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.telefone = telefone != null ? telefone.trim() : null;
        this.endereco = endereco != null ? endereco.trim() : null;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public BusinessId getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public BusinessStatus getStatus() {
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
     * Verifica se o Business está ativo para realizar operações.
     */
    public boolean isAtivo() {
        return status == BusinessStatus.ATIVO || status == BusinessStatus.TRIAL;
    }

    /**
     * Verifica se o Business pode criar novos agendamentos.
     */
    public boolean podeCriarAgendamentos() {
        return status == BusinessStatus.ATIVO;
    }

    /**
     * Atualiza informações de contato do negócio.
     */
    public void atualizarContato(String telefone, String endereco) {
        this.telefone = telefone != null ? telefone.trim() : null;
        this.endereco = endereco != null ? endereco.trim() : null;
        tocar();
    }

    /**
     * Atualiza o nome do negócio.
     */
    public void atualizarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do negócio não pode ser vazio");
        }
        this.nome = nome.trim();
        tocar();
    }

    /**
     * Ativa o Business (transição para ATIVO).
     */
    public void ativar() {
        if (status == BusinessStatus.DELETADO) {
            throw new IllegalStateException("Não é possível ativar um Business deletado");
        }
        this.status = BusinessStatus.ATIVO;
        tocar();
    }

    /**
     * Desativa o Business (transição para INATIVO).
     */
    public void desativar() {
        if (status == BusinessStatus.DELETADO) {
            throw new IllegalStateException("Não é possível desativar um Business deletado");
        }
        this.status = BusinessStatus.INATIVO;
        tocar();
    }

    /**
     * Suspende o Business (transição para SUSPENSO).
     */
    public void suspender() {
        if (status == BusinessStatus.DELETADO) {
            throw new IllegalStateException("Não é possível suspender um Business deletado");
        }
        this.status = BusinessStatus.SUSPENSO;
        tocar();
    }

    /**
     * Marca o Business como deletado.
     */
    public void deletar() {
        this.status = BusinessStatus.DELETADO;
        tocar();
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
