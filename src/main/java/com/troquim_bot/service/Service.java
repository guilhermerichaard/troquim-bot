package com.troquim_bot.service;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Aggregate Root que representa um serviço oferecido por um Business.
 *
 * Responsabilidades:
 * - Gerenciar informações do serviço (nome, descrição, duração, preço)
 * - Controlar o ciclo de vida do serviço (ATIVO, INATIVO)
 * - Proteger invariants de negócio
 *
 * O {@link BusinessId} faz parte da identidade do serviço: catálogo é sempre POR NEGÓCIO.
 * Não existe serviço global, e nenhuma consulta de catálogo é válida sem tenant.
 *
 * PREÇO É OPCIONAL e fica fora do MVP atual. O contrato do domínio expressa ausência com
 * {@link Optional#empty()} em {@link #getPreco()} — nunca devolvendo {@code null} nem
 * convertendo ausência em zero. A criação passa por fábricas explícitas
 * ({@link #novoSemPreco} / {@link #novoComPreco}), de modo que "sem preço" seja sempre uma
 * decisão declarada. Coluna anulável existe apenas no adapter JPA, não aqui.
 */
public class Service {

    private final ServiceId id;
    private final BusinessId businessId;
    private String nome;
    private String descricao;
    private ServiceDuration duracao;
    private Money preco;
    private ServiceStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Serviço SEM preço — o caso do MVP. Explícito no nome para que "sem preço" seja
     * decisão declarada, nunca consequência de passar {@code null} sem perceber.
     */
    public static Service novoSemPreco(ServiceId id, BusinessId businessId, String nome,
                                       String descricao, ServiceDuration duracao) {
        return new Service(id, businessId, nome, descricao, duracao, null);
    }

    /**
     * Serviço COM preço. {@code preco} é obrigatório aqui: quem escolhe esta fábrica está
     * afirmando que há valor definido.
     */
    public static Service novoComPreco(ServiceId id, BusinessId businessId, String nome,
                                       String descricao, ServiceDuration duracao, Money preco) {
        if (preco == null) {
            throw new IllegalArgumentException("Preço é obrigatório em novoComPreco; use novoSemPreco");
        }
        return new Service(id, businessId, nome, descricao, duracao, preco);
    }

    /**
     * Reconstituição a partir da persistência. Uso EXCLUSIVO do adapter: só a
     * Infrastructure conhece "coluna nula", e é ela quem traduz isso para ausência.
     */
    public static Service reconstituir(ServiceId id, BusinessId businessId, String nome,
                                       String descricao, ServiceDuration duracao, Money preco,
                                       ServiceStatus status, LocalDateTime criadoEm,
                                       LocalDateTime atualizadoEm) {
        return new Service(id, businessId, nome, descricao, duracao, preco, status, criadoEm, atualizadoEm);
    }

    /**
     * Construtor de criação. Privado: a intenção sobre preço passa pelas fábricas.
     * Inicia com status ATIVO.
     */
    private Service(ServiceId id, BusinessId businessId, String nome, String descricao,
                    ServiceDuration duracao, Money preco) {
        if (id == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (duracao == null) {
            throw new IllegalArgumentException("Duração é obrigatória");
        }

        this.id = id;
        this.businessId = businessId;
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
    private Service(ServiceId id, BusinessId businessId, String nome, String descricao,
                    ServiceDuration duracao, Money preco,
                    ServiceStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.nome = nome.trim();
        this.descricao = descricao != null ? descricao.trim() : null;
        this.duracao = duracao;
        this.preco = preco;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public BusinessId getBusinessId() {
        return businessId;
    }

    /** Guarda de isolamento: usada por Application antes de expor ou agendar. */
    public boolean pertenceAo(BusinessId outro) {
        return outro != null && businessId.equals(outro);
    }

    /** Preço é opcional no MVP; sem preço definido, nada deve ser exibido ao cliente. */
    public boolean temPreco() {
        return preco != null;
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

    /**
     * Preço é OPCIONAL no MVP. O domínio nunca devolve {@code null} como contrato:
     * ausência de preço é {@link Optional#empty()}, e quem consome é obrigado a decidir
     * o que fazer com isso — não existe caminho onde "sem preço" vire zero por descuido.
     */
    public Optional<Money> getPreco() {
        return Optional.ofNullable(preco);
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
     * Define um preço. Passar {@code null} é erro de programação, não "apagar o preço":
     * para remover, use {@link #removerPreco()}. Duas intenções distintas, dois métodos.
     */
    public void definirPreco(Money preco) {
        if (preco == null) {
            throw new IllegalArgumentException("Preço não pode ser nulo; use removerPreco()");
        }
        this.preco = preco;
        tocar();
    }

    /** Volta o serviço ao estado "não precificado". */
    public void removerPreco() {
        this.preco = null;
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