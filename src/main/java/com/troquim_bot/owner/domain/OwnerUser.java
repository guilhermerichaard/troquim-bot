package com.troquim_bot.owner.domain;

import com.troquim_bot.business.BusinessId;

import java.util.UUID;

/**
 * O dono de um negócio — quem acessa a área privada /app.
 *
 * Cada dono pertence a EXATAMENTE um {@link BusinessId}, e é esse vínculo que resolve o
 * tenant do /app (nunca o PilotTenantProvider). A senha vive só como hash: o texto claro
 * nunca entra no domínio.
 *
 * Aggregate root pequeno de propósito — identidade e acesso, não perfil. Nome, contato e
 * o resto do negócio moram em Business.
 */
public class OwnerUser {

    private final OwnerUserId id;
    private final BusinessId businessId;
    private final String email;
    private final String senhaHash;
    private final OwnerUserStatus status;

    public OwnerUser(OwnerUserId id, BusinessId businessId, String email,
                     String senhaHash, OwnerUserStatus status) {
        if (id == null) throw new IllegalArgumentException("OwnerUserId é obrigatório");
        if (businessId == null) throw new IllegalArgumentException("BusinessId é obrigatório");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email é obrigatório");
        if (senhaHash == null || senhaHash.isBlank()) throw new IllegalArgumentException("senhaHash é obrigatório");
        if (status == null) throw new IllegalArgumentException("status é obrigatório");
        this.id = id;
        this.businessId = businessId;
        this.email = email.trim().toLowerCase();
        this.senhaHash = senhaHash;
        this.status = status;
    }

    public static OwnerUser novo(BusinessId businessId, String email, String senhaHash) {
        return new OwnerUser(OwnerUserId.from(UUID.randomUUID()), businessId, email,
                senhaHash, OwnerUserStatus.ATIVO);
    }

    public boolean podeAutenticar() {
        return status == OwnerUserStatus.ATIVO;
    }

    public boolean pertenceAoTenant(BusinessId businessId) {
        return businessId != null && businessId.equals(this.businessId);
    }

    public OwnerUserId getId() { return id; }
    public BusinessId getBusinessId() { return businessId; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public OwnerUserStatus getStatus() { return status; }

    /** Nunca inclui o hash da senha. */
    @Override
    public String toString() {
        return "OwnerUser[id=" + id + ", businessId=" + businessId + ", status=" + status + "]";
    }
}
