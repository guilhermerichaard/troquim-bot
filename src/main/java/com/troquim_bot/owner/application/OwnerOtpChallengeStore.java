package com.troquim_bot.owner.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.domain.OwnerUserId;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OwnerOtpChallengeStore {
    void salvar(OwnerOtpChallenge challenge);
    Optional<OwnerOtpChallenge> buscar(UUID id);
    long contarDesde(String phoneHash, LocalDateTime since);
    void invalidarAtivos(OwnerUserId ownerId, BusinessId businessId, OwnerOtpPurpose purpose, LocalDateTime now);
}
