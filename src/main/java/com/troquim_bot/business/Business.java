package com.troquim_bot.business;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate Root que representa um negócio cliente do Troquim.
 * 
 * Responsabilidades:
 * - Gerenciar configurações do negócio (nome, contato, horários)
 * - Controlar o ciclo de vida do negócio (TRIAL, ATIVO, INATIVO, SUSPENSO, DELETADO)
 * - Proteger invariants de negócio
 * - Ser a raiz de referência para todos os Aggregates relacionados
 */
public class Business {

    private final BusinessId id;
    private String nome;
    private String telefone;
    private String endereco;
    private BusinessHours horarioFuncionamento;
    private BusinessStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Business.
     * Inicia com status TRIAL.
     */
    public Business(BusinessId id, String nome, String telefone, String endereco, BusinessHours horarioFuncionamento) {
        if (id == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do negócio é obrigatório");
        }
        if (telefone == null && endereco == null) {
            throw new IllegalArgumentException("Deve ter pelo menos um contato (telefone ou endereço)");
        }
        if (horarioFuncionamento == null) {
            throw new IllegalArgumentException("Horário de funcionamento é obrigatório");
        }

        this.id = id;
        this.nome = nome.trim();
        this.telefone = telefone != null ? telefone.trim() : null;
        this.endereco = endereco != null ? endereco.trim() : null;
        this.horarioFuncionamento = horarioFuncionamento;
        this.status = BusinessStatus.TRIAL;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Business existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Business(BusinessId id, String nome, String telefone, String endereco, 
                    BusinessHours horarioFuncionamento, BusinessStatus status, 
                    LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
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
        this.horarioFuncionamento = horarioFuncionamento;
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

    public BusinessHours getHorarioFuncionamento() {
        return horarioFuncionamento;
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
     * Atualiza o horário de funcionamento do negócio.
     */
    public void atualizarHorarioFuncionamento(BusinessHours novoHorario) {
        if (novoHorario == null) {
            throw new IllegalArgumentException("Horário de funcionamento não pode ser nulo");
        }
        this.horarioFuncionamento = novoHorario;
        tocar();
    }

    /**
     * Atualiza informações de contato do negócio.
     */
    public void atualizarContato(String telefone, String endereco) {
        if (telefone == null && endereco == null) {
            throw new IllegalArgumentException("Deve ter pelo menos um contato (telefone ou endereço)");
        }
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

    /**
     * Verifica se o Business está em horário de funcionamento.
     */
    public boolean estaEmHorarioFuncionamento(java.time.DayOfWeek dia, java.time.LocalTime horario) {
        if (horarioFuncionamento == null) {
            return false;
        }
        
        DiaSemana diaSemana = converterDiaSemana(dia);
        if (!horarioFuncionamento.isDiaFuncionamento(diaSemana)) {
            return false;
        }
        
        return horarioFuncionamento.estaAberto(horario);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    private DiaSemana converterDiaSemana(java.time.DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }
}