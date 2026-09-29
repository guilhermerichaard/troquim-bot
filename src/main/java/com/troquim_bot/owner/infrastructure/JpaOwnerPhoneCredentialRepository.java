package com.troquim_bot.owner.infrastructure;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.application.OwnerPhoneCredentialRepository;
import com.troquim_bot.owner.domain.OwnerPhoneCredential;
import com.troquim_bot.owner.domain.OwnerUserId;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaOwnerPhoneCredentialRepository implements OwnerPhoneCredentialRepository {
    private final SpringDataOwnerPhoneCredentialRepository repository;
    public JpaOwnerPhoneCredentialRepository(SpringDataOwnerPhoneCredentialRepository repository){this.repository=repository;}
    public Optional<OwnerPhoneCredential> buscarPorTelefone(String phoneE164){return repository.findByPhoneE164(phoneE164).map(JpaOwnerPhoneCredentialRepository::toDomain);}
    public Optional<OwnerPhoneCredential> buscarPorDono(OwnerUserId ownerId, BusinessId businessId){return repository.findByOwnerIdAndBusinessId(ownerId.getValue(), businessId.getValue()).map(JpaOwnerPhoneCredentialRepository::toDomain);}
    public void salvar(OwnerPhoneCredential c){repository.save(new OwnerPhoneCredentialJpaEntity(c.ownerId().getValue(), c.businessId().getValue(), c.phoneE164(), c.verifiedAt()));}
    private static OwnerPhoneCredential toDomain(OwnerPhoneCredentialJpaEntity e){return new OwnerPhoneCredential(OwnerUserId.from(e.getOwnerId()), BusinessId.from(e.getBusinessId()), e.getPhoneE164(), e.getVerifiedAt());}
}
