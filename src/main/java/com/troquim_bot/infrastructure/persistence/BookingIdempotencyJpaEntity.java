package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Recibo de um comando de confirmação. Tabela de INTEGRAÇÃO/controle, não entidade de
 * negócio: o agendamento em si vive em {@code appointments}.
 *
 * {@code command_key} é a chave primária — é ela, e não uma busca por agendamentos do
 * cliente, que reconhece um retry.
 */
@Entity
@Table(name = "booking_idempotency")
public class BookingIdempotencyJpaEntity {

    @Id
    @Column(name = "command_key", nullable = false, length = 160)
    private String commandKey;

    /** Tenant dono do comando. Escopa a regra "uma base, um agendamento" por negócio. */
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** Base isolada (o flow_token). A regra do MVP incide sobre (business_id, command_base). */
    @Column(name = "command_base", nullable = false, length = 80)
    private String commandBase;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    /** Nulo enquanto reivindicado e ainda não concluído (visível só dentro da transação). */
    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "outcome_status", length = 20)
    private String outcomeStatus;

    @Column(name = "outcome_servico", length = 120)
    private String outcomeServico;

    @Column(name = "outcome_data", length = 10)
    private String outcomeData;

    @Column(name = "outcome_horario", length = 5)
    private String outcomeHorario;

    @Column(name = "outcome_nome", length = 120)
    private String outcomeNome;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected BookingIdempotencyJpaEntity() {
    }

    public String getCommandKey() {
        return commandKey;
    }

    public String getCommandBase() {
        return commandBase;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public String getOutcomeStatus() {
        return outcomeStatus;
    }

    public String getOutcomeServico() {
        return outcomeServico;
    }

    public String getOutcomeData() {
        return outcomeData;
    }

    public String getOutcomeHorario() {
        return outcomeHorario;
    }

    public String getOutcomeNome() {
        return outcomeNome;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /** Concluído = tem desfecho gravado. Sem isto, a linha é apenas uma reivindicação. */
    public boolean concluido() {
        return outcomeStatus != null;
    }
}
