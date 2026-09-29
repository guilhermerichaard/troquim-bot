package com.troquim_bot.owner.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.domain.OwnerUserId;
import java.time.LocalDateTime;
import java.util.UUID;

public record OwnerOtpChallenge(
        UUID id,
        OwnerUserId ownerId,
        BusinessId businessId,
        String phoneE164,
        String phoneHash,
        String codeMac,
        OwnerOtpPurpose purpose,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        int attemptsRemaining,
        LocalDateTime consumedAt
) {
    public OwnerOtpChallenge {
        if (id == null || ownerId == null || businessId == null || purpose == null) {
            throw new IllegalArgumentException("challenge incompleto");
        }
        if (phoneE164 == null || !phoneE164.matches("[1-9][0-9]{7,14}")) throw new IllegalArgumentException("telefone inválido");
        if (phoneHash == null || phoneHash.isBlank() || codeMac == null || codeMac.isBlank()) throw new IllegalArgumentException("prova inválida");
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiração inválida");
        if (attemptsRemaining < 0) throw new IllegalArgumentException("tentativas inválidas");
    }

    public boolean utilizavel(LocalDateTime now) {
        return consumedAt == null && attemptsRemaining > 0 && expiresAt.isAfter(now);
    }

    public OwnerOtpChallenge falhou() {
        return new OwnerOtpChallenge(id, ownerId, businessId, phoneE164, phoneHash, codeMac, purpose,
                createdAt, expiresAt, Math.max(0, attemptsRemaining - 1), consumedAt);
    }

    public OwnerOtpChallenge consumido(LocalDateTime now) {
        return new OwnerOtpChallenge(id, ownerId, businessId, phoneE164, phoneHash, codeMac, purpose,
                createdAt, expiresAt, attemptsRemaining, now);
    }
}
