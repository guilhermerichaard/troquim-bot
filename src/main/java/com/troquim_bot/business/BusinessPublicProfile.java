package com.troquim_bot.business;

import java.time.LocalDateTime;

/**
 * Aggregate Root da identidade PÚBLICA de um negócio — nome, descrição e contato tal como um
 * visitante veria numa página pública futura, e o slug que o identifica na URL.
 *
 * SEPARADO de {@link Business} DE PROPÓSITO: {@link Business} é a identidade INTERNA do
 * tenant (dado administrativo, nunca exposto). Misturar os dois faria qualquer alteração de
 * publicação mexer no mesmo agregado que controla o ciclo de vida do tenant — e faria o dado
 * administrativo vazar para fora assim que o perfil público fosse servido.
 *
 * Um perfil por negócio: a identidade deste agregado é o próprio {@link BusinessId}
 * proprietário, não um id próprio.
 */
public class BusinessPublicProfile {

    private final BusinessId businessId;
    private BusinessSlug slug;
    private String nomePublico;
    private String descricaoCurta;
    private String telefonePublico;
    private String enderecoPublico;
    private PublicationStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /** Construtor de criação: todo perfil nasce DRAFT — publicar é uma decisão explícita à parte. */
    public BusinessPublicProfile(BusinessId businessId, BusinessSlug slug, String nomePublico,
                                 String descricaoCurta, String telefonePublico, String enderecoPublico) {
        this(businessId, slug, nomePublico, descricaoCurta, telefonePublico, enderecoPublico,
                PublicationStatus.DRAFT, LocalDateTime.now(), LocalDateTime.now());
    }

    /** Construtor de reconstituição, usado apenas pela infraestrutura. */
    public BusinessPublicProfile(BusinessId businessId, BusinessSlug slug, String nomePublico,
                                 String descricaoCurta, String telefonePublico, String enderecoPublico,
                                 PublicationStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (slug == null) {
            throw new IllegalArgumentException("Slug é obrigatório");
        }
        if (nomePublico == null || nomePublico.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome público é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status de publicação é obrigatório");
        }

        this.businessId = businessId;
        this.slug = slug;
        this.nomePublico = nomePublico.trim();
        this.descricaoCurta = normalizado(descricaoCurta);
        this.telefonePublico = normalizado(telefonePublico);
        this.enderecoPublico = normalizado(enderecoPublico);
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    /**
     * Substitui a configuração do perfil. NÃO muda o status de publicação: reconfigurar um
     * perfil publicado o mantém publicado (com o dado novo), e reconfigurar um rascunho o
     * mantém rascunho — mudar visibilidade é decisão de {@code PublicarPerfilPublico} /
     * {@code DespublicarPerfilPublico}, nunca efeito colateral de uma edição de campo.
     */
    public void atualizarConfiguracao(BusinessSlug novoSlug, String nomePublico, String descricaoCurta,
                                      String telefonePublico, String enderecoPublico) {
        if (novoSlug == null) {
            throw new IllegalArgumentException("Slug é obrigatório");
        }
        if (nomePublico == null || nomePublico.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome público é obrigatório");
        }
        this.slug = novoSlug;
        this.nomePublico = nomePublico.trim();
        this.descricaoCurta = normalizado(descricaoCurta);
        this.telefonePublico = normalizado(telefonePublico);
        this.enderecoPublico = normalizado(enderecoPublico);
        tocar();
    }

    /**
     * DRAFT → PUBLISHED. IDEMPOTENTE: publicar um perfil já PUBLISHED não é erro, é um no-op
     * de estado — a repetição é o comportamento normal de um retry, não uma exceção.
     */
    public void publicar() {
        exigirProntoParaPublicar();
        this.status = PublicationStatus.PUBLISHED;
        tocar();
    }

    /** PUBLISHED → DRAFT. IDEMPOTENTE pelo mesmo motivo de {@link #publicar()}. */
    public void despublicar() {
        this.status = PublicationStatus.DRAFT;
        tocar();
    }

    public boolean publicado() {
        return status == PublicationStatus.PUBLISHED;
    }

    private void exigirProntoParaPublicar() {
        if (slug == null) {
            throw new IllegalStateException("Perfil sem slug não pode ser publicado");
        }
        if (nomePublico == null || nomePublico.isBlank()) {
            throw new IllegalStateException("Perfil sem nome público não pode ser publicado");
        }
    }

    private static String normalizado(String valor) {
        if (valor == null) {
            return null;
        }
        String semEspacos = valor.trim();
        return semEspacos.isEmpty() ? null : semEspacos;
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // ==================== GETTERS ====================

    public BusinessId getBusinessId() {
        return businessId;
    }

    public BusinessSlug getSlug() {
        return slug;
    }

    public String getNomePublico() {
        return nomePublico;
    }

    public String getDescricaoCurta() {
        return descricaoCurta;
    }

    public String getTelefonePublico() {
        return telefonePublico;
    }

    public String getEnderecoPublico() {
        return enderecoPublico;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
