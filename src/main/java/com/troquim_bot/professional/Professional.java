package com.troquim_bot.professional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate Root que representa um profissional do salão.
 * 
 * Responsabilidades:
 * - Gerenciar dados do profissional (nome, especialidades, telefone)
 * - Controlar o ciclo de vida do profissional (ATIVO, INATIVO)
 * - Proteger invariants de negócio
 */
public class Professional {

    private final ProfessionalId id;
    private String nome;
    private Set<String> especialidades;
    private String telefone;
    private ProfessionalStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Professional.
     * Inicia com status ATIVO.
     */
    public Professional(ProfessionalId id, String nome, Set<String> especialidades, String telefone) {
        if (id == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (especialidades == null || especialidades.isEmpty()) {
            throw new IllegalArgumentException("Especialidades são obrigatórias");
        }
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.especialidades = new HashSet<>(especialidades);
        this.telefone = telefone.trim();
        this.status = ProfessionalStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Professional existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Professional(ProfessionalId id, String nome, Set<String> especialidades, String telefone,
                        ProfessionalStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (especialidades == null || especialidades.isEmpty()) {
            throw new IllegalArgumentException("Especialidades são obrigatórias");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.especialidades = new HashSet<>(especialidades);
        this.telefone = telefone != null ? telefone.trim() : null;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public ProfessionalId getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Set<String> getEspecialidades() {
        return new HashSet<>(especialidades);
    }

    public String getTelefone() {
        return telefone;
    }

    public ProfessionalStatus getStatus() {
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
     * Verifica se o Professional está ativo.
     */
    public boolean isAtivo() {
        return status == ProfessionalStatus.ATIVO;
    }

    /**
     * Atualiza o nome do profissional.
     */
    public void atualizarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional não pode ser vazio");
        }
        this.nome = nome.trim();
        tocar();
    }

    /**
     * Atualiza as especialidades do profissional.
     */
    public void atualizarEspecialidades(Set<String> especialidades) {
        if (especialidades == null || especialidades.isEmpty()) {
            throw new IllegalArgumentException("Especialidades são obrigatórias");
        }
        this.especialidades = new HashSet<>(especialidades);
        tocar();
    }

    /**
     * Atualiza o telefone do profissional.
     */
    public void atualizarTelefone(String telefone) {
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }
        this.telefone = telefone.trim();
        tocar();
    }

    /**
     * Desativa o Professional (transição para INATIVO).
     */
    public void desativar() {
        this.status = ProfessionalStatus.INATIVO;
        tocar();
    }

    /**
     * Ativa o Professional (transição para ATIVO).
     */
    public void ativar() {
        this.status = ProfessionalStatus.ATIVO;
        tocar();
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}