package com.troquim_bot.owner.domain;

import com.troquim_bot.business.BusinessId;
import java.time.LocalDateTime;

public record OwnerPhoneCredential(
        OwnerUserId ownerId,
        BusinessId businessId,
        String phoneE164,
        LocalDateTime verifiedAt
) {
    public OwnerPhoneCredential {
        if (ownerId == null) throw new IllegalArgumentException("ownerId é obrigatório");
        if (businessId == null) throw new IllegalArgumentException("businessId é obrigatório");
        if (phoneE164 == null || !phoneE164.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("phoneE164 inválido");
        }
        if (verifiedAt == null) throw new IllegalArgumentException("verifiedAt é obrigatório");
    }

    public String masked() {
        int keep = Math.min(4, phoneE164.length());
        return "•••• " + phoneE164.substring(phoneE164.length() - keep);
    }
}
