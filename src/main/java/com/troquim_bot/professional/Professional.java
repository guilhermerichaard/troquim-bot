package com.troquim_bot.professional;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate Root que representa um profissional do salão.
 *
 * Responsabilidades:
 * - Gerenciar dados do profissional (nome, telefone, habilitações)
 * - Controlar o ciclo de vida do profissional (ATIVO, INATIVO)
 * - Proteger invariants de negócio
 *
 * O {@link BusinessId} faz parte da identidade: profissional pertence a UM negócio.
 *
 * VÍNCULO PROFISSIONAL × SERVIÇO — {@code servicosHabilitados} é a associação OFICIAL,
 * por {@link ServiceId}. {@code especialidades} continua existindo, mas é texto livre
 * DESCRITIVO: não tem integridade referencial nem escopo de tenant e, por isso, NÃO
 * pode ser usado para decidir quem atende o quê.
 *
 * A arquitetura suporta MÚLTIPLOS profissionais por negócio. Um negócio com um único
 * profissional é um caso particular do modelo, não uma premissa dele.
 */
public class Professional {

    private final ProfessionalId id;
    private final BusinessId businessId;
    private String nome;
    private Set<String> especialidades;
    private Set<ServiceId> servicosHabilitados;
    private String telefone;
    private ProfessionalStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Professional.
     * Inicia com status ATIVO.
     */
    public Professional(ProfessionalId id, BusinessId businessId, String nome,
                        Set<ServiceId> servicosHabilitados, Set<String> especialidades, String telefone) {
        if (id == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.nome = nome.trim();
        this.servicosHabilitados = servicosHabilitados == null
                ? new HashSet<>() : new HashSet<>(servicosHabilitados);
        this.especialidades = especialidades == null ? new HashSet<>() : new HashSet<>(especialidades);
        this.telefone = telefone.trim();
        this.status = ProfessionalStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Professional existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Professional(ProfessionalId id, BusinessId businessId, String nome,
                        Set<ServiceId> servicosHabilitados, Set<String> especialidades, String telefone,
                        ProfessionalStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.nome = nome.trim();
        this.servicosHabilitados = servicosHabilitados == null
                ? new HashSet<>() : new HashSet<>(servicosHabilitados);
        this.especialidades = especialidades == null ? new HashSet<>() : new HashSet<>(especialidades);
        this.telefone = telefone != null ? telefone.trim() : null;
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

    public Set<ServiceId> getServicosHabilitados() {
        return Collections.unmodifiableSet(new HashSet<>(servicosHabilitados));
    }

    /**
     * Decide se este profissional atende um serviço. Profissional INATIVO não atende nada,
     * mesmo habilitado — a regra vive aqui, não em quem consulta.
     */
    public boolean atende(ServiceId servico) {
        return servico != null
                && status == ProfessionalStatus.ATIVO
                && servicosHabilitados.contains(servico);
    }

    public void habilitarPara(ServiceId servico) {
        if (servico == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório para habilitar");
        }
        servicosHabilitados.add(servico);
        tocar();
    }

    public void desabilitarPara(ServiceId servico) {
        if (servico != null && servicosHabilitados.remove(servico)) {
            tocar();
        }
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