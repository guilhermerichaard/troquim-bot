package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA para persistência de Customer.
 *
 * Isolamento por tenant: business_id NOT NULL, phone_e164 NOT NULL e unicidade
 * lógica UNIQUE (business_id, phone_e164), com índice por business_id para as
 * consultas por tenant. No PostgreSQL, o schema/constraints são autoridade do
 * Flyway (Hibernate apenas valida); estas anotações mantêm o schema equivalente
 * quando o Hibernate gera o schema do H2 nos testes (sem modelos divergentes).
 */
@Entity
@Table(
        name = "customers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_customers_business_phone",
                columnNames = {"business_id", "phone_e164"}),
        indexes = @Index(name = "idx_customers_business_id", columnList = "business_id")
)
public class CustomerJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "business_id", nullable = false, columnDefinition = "UUID")
    private UUID businessId;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "apelido", length = 50)
    private String apelido;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_atendimentos", nullable = false)
    private int totalAtendimentos;

    @Column(name = "ultimo_atendimento")
    private LocalDateTime ultimoAtendimento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /**
     * Construtor padrão exigido pelo JPA.
     */
    protected CustomerJpaEntity() {}

    public CustomerJpaEntity(UUID id, UUID businessId, String phoneE164,
                             String firstName, String lastName, String phone,
                             String apelido, String notes, String status,
                             int totalAtendimentos, LocalDateTime ultimoAtendimento,
                             LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.businessId = businessId;
        this.phoneE164 = phoneE164;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.apelido = apelido;
        this.notes = notes;
        this.status = status;
        this.totalAtendimentos = totalAtendimentos;
        this.ultimoAtendimento = ultimoAtendimento;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getPhoneE164() { return phoneE164; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getApelido() { return apelido; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public int getTotalAtendimentos() { return totalAtendimentos; }
    public LocalDateTime getUltimoAtendimento() { return ultimoAtendimento; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}