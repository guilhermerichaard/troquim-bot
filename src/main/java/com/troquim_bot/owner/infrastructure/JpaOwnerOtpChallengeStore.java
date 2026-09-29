package com.troquim_bot.owner.infrastructure;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.application.*;
import com.troquim_bot.owner.domain.OwnerUserId;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaOwnerOtpChallengeStore implements OwnerOtpChallengeStore {
    private final SpringDataOwnerOtpChallengeRepository repository;
    public JpaOwnerOtpChallengeStore(SpringDataOwnerOtpChallengeRepository repository){this.repository=repository;}
    public void salvar(OwnerOtpChallenge c){repository.save(toEntity(c));}
    public Optional<OwnerOtpChallenge> buscar(UUID id){return repository.findById(id).map(JpaOwnerOtpChallengeStore::toDomain);}
    public long contarDesde(String phoneHash, LocalDateTime since){return repository.countByPhoneHashAndCreatedAtAfter(phoneHash,since);}
    public void invalidarAtivos(OwnerUserId ownerId, BusinessId businessId, OwnerOtpPurpose purpose, LocalDateTime now){
        repository.findAllByOwnerIdAndBusinessIdAndPurposeAndConsumedAtIsNull(ownerId.getValue(),businessId.getValue(),purpose.name())
                .stream().map(JpaOwnerOtpChallengeStore::toDomain).filter(c->c.utilizavel(now))
                .forEach(c->repository.save(toEntity(c.consumido(now))));
    }
    private static OwnerOtpChallengeJpaEntity toEntity(OwnerOtpChallenge c){return new OwnerOtpChallengeJpaEntity(c.id(),c.ownerId().getValue(),c.businessId().getValue(),c.phoneE164(),c.phoneHash(),c.codeMac(),c.purpose().name(),c.createdAt(),c.expiresAt(),c.attemptsRemaining(),c.consumedAt());}
    private static OwnerOtpChallenge toDomain(OwnerOtpChallengeJpaEntity e){return new OwnerOtpChallenge(e.getId(),OwnerUserId.from(e.getOwnerId()),BusinessId.from(e.getBusinessId()),e.getPhoneE164(),e.getPhoneHash(),e.getCodeMac(),OwnerOtpPurpose.valueOf(e.getPurpose()),e.getCreatedAt(),e.getExpiresAt(),e.getAttemptsRemaining(),e.getConsumedAt());}
}
