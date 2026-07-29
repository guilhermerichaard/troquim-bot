package com.troquim_bot.owner.infrastructure;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.application.OwnerSession;
import com.troquim_bot.owner.application.OwnerSessionStore;
import com.troquim_bot.owner.domain.OwnerUserId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaOwnerSessionStore implements OwnerSessionStore {

    private final SpringDataOwnerSessionRepository repository;

    public JpaOwnerSessionStore(SpringDataOwnerSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public OwnerSession criar(OwnerSession session) {
        repository.save(new OwnerSessionJpaEntity(
                session.tokenHash(), session.ownerId().getValue(), session.businessId().getValue(),
                session.criadaEm(), session.expiraEm()));
        return session;
    }

    @Override
    public Optional<OwnerSession> buscarPorTokenHash(String tokenHash) {
        if (tokenHash == null) return Optional.empty();
        return repository.findById(tokenHash).map(e -> new OwnerSession(
                e.getTokenHash(), OwnerUserId.from(e.getOwnerId()), BusinessId.from(e.getBusinessId()),
                e.getCriadaEm(), e.getExpiraEm()));
    }

    @Override
    public void revogarPorTokenHash(String tokenHash) {
        if (tokenHash != null) repository.deleteById(tokenHash);
    }
}
