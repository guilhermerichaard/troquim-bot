package com.troquim_bot.owner.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.domain.OwnerPhoneCredential;
import com.troquim_bot.owner.domain.OwnerUserId;
import java.util.Optional;

public interface OwnerPhoneCredentialRepository {
    Optional<OwnerPhoneCredential> buscarPorTelefone(String phoneE164);
    Optional<OwnerPhoneCredential> buscarPorDono(OwnerUserId ownerId, BusinessId businessId);
    void salvar(OwnerPhoneCredential credential);
}
