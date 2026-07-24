package com.troquim_bot.whatsapp.flow.infrastructure.persistence;

import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sessão de WhatsApp Flow. Tabela de INTEGRAÇÃO, não entidade de negócio: guarda a
 * amarração token → cliente/tenant e o recibo de confirmação (idempotência). O
 * agendamento de fato mora em reservations/appointments.
 *
 * O {@code flow_token} é a chave primária: garante, no banco, que um token só produz um
 * agendamento, mesmo sob reentregas concorrentes da Meta.
 *
 * O status é persistido como STRING (não ordinal) para que inserir um estado novo no
 * meio do enum não reinterprete linhas antigas.
 */
@Entity
@Table(name = "whatsapp_flow_sessions")
public class WhatsAppFlowSessionJpaEntity {

    @Id
    @Column(name = "flow_token", nullable = false, length = 100)
    private String flowToken;

    @Column(name = "telefone", nullable = false, length = 30)
    private String telefone;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FlowSessionStatus status;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "confirmado_servico", length = 120)
    private String confirmadoServico;

    @Column(name = "confirmado_data", length = 10)
    private String confirmadoData;

    @Column(name = "confirmado_horario", length = 5)
    private String confirmadoHorario;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected WhatsAppFlowSessionJpaEntity() {
    }

    public WhatsAppFlowSessionJpaEntity(String flowToken, String telefone, UUID businessId,
                                        LocalDateTime expiraEm, LocalDateTime criadoEm) {
        this.flowToken = flowToken;
        this.telefone = telefone;
        this.businessId = businessId;
        this.status = FlowSessionStatus.ABERTA;
        this.expiraEm = expiraEm;
        this.criadoEm = criadoEm;
        this.atualizadoEm = criadoEm;
    }

    public String getFlowToken() {
        return flowToken;
    }

    public String getTelefone() {
        return telefone;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public FlowSessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public String getConfirmadoServico() {
        return confirmadoServico;
    }

    public String getConfirmadoData() {
        return confirmadoData;
    }

    public String getConfirmadoHorario() {
        return confirmadoHorario;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    /** Grava o desfecho e conclui a sessão — as duas coisas juntas, nunca separadas. */
    public void registrarConfirmacao(String servico, String dataIso, String horario,
                                     LocalDateTime agora) {
        this.confirmadoServico = servico;
        this.confirmadoData = dataIso;
        this.confirmadoHorario = horario;
        this.status = FlowSessionStatus.CONCLUIDA;
        this.atualizadoEm = agora;
    }

    public void invalidar(LocalDateTime agora) {
        this.status = FlowSessionStatus.INVALIDADA;
        this.atualizadoEm = agora;
    }

    public boolean temConfirmacao() {
        return confirmadoData != null && confirmadoHorario != null;
    }

    /**
     * Status efetivo na leitura: uma sessão ABERTA que passou do vencimento é EXPIRADA
     * mesmo sem nenhuma rotina ter atualizado a linha.
     */
    public FlowSessionStatus statusEfetivo(LocalDateTime agora) {
        if (status == FlowSessionStatus.ABERTA && expiraEm != null && agora.isAfter(expiraEm)) {
            return FlowSessionStatus.EXPIRADA;
        }
        return status;
    }
}
