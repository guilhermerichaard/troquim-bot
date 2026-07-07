package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entidade JPA para persistência de ConversationState.
 * Mapeia a tabela "conversation_states" no banco H2.
 * 
 * O estado completo da conversa (step, drafts, nome, etc.)
 * é serializado como JSON na coluna "state_json".
 */
@Entity
@Table(name = "conversation_states")
public class ConversationStateJpaEntity {

    @Id
    @Column(name = "numero", nullable = false, length = 50)
    private String numero;

    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /**
     * Construtor padrão exigido pelo JPA.
     */
    protected ConversationStateJpaEntity() {}

    public ConversationStateJpaEntity(String numero, String stateJson,
                                      LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.numero = numero;
        this.stateJson = stateJson;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public String getNumero() { return numero; }
    public String getStateJson() { return stateJson; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }

    // ==================== SETTERS ====================

    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}