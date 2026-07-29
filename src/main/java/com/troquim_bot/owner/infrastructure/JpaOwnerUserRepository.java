package com.troquim_bot.owner.infrastructure;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.application.OwnerUserRepository;
import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.domain.OwnerUserId;
import com.troquim_bot.owner.domain.OwnerUserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JpaOwnerUserRepository implements OwnerUserRepository {

    private final SpringDataOwnerUserRepository repository;

    public JpaOwnerUserRepository(SpringDataOwnerUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public OwnerUser salvar(OwnerUser owner) {
        LocalDateTime agora = LocalDateTime.now();
        OwnerUserJpaEntity existente = repository.findById(owner.getId().getValue()).orElse(null);
        LocalDateTime criadoEm = existente != null ? existente.getCriadoEm() : agora;
        repository.save(new OwnerUserJpaEntity(
                owner.getId().getValue(), owner.getBusinessId().getValue(), owner.getEmail(),
                owner.getSenhaHash(), owner.getStatus().name(), criadoEm, agora));
        return owner;
    }

    @Override
    public Optional<OwnerUser> buscarPorEmail(String email) {
        if (email == null) return Optional.empty();
        return repository.findByEmail(email.trim().toLowerCase()).map(JpaOwnerUserRepository::paraDominio);
    }

    @Override
    public Optional<OwnerUser> buscarPorId(OwnerUserId id) {
        if (id == null) return Optional.empty();
        return repository.findById(id.getValue()).map(JpaOwnerUserRepository::paraDominio);
    }

    @Override
    public boolean existePorEmail(String email) {
        return email != null && repository.existsByEmail(email.trim().toLowerCase());
    }

    private static OwnerUser paraDominio(OwnerUserJpaEntity e) {
        return new OwnerUser(OwnerUserId.from(e.getId()), BusinessId.from(e.getBusinessId()),
                e.getEmail(), e.getSenhaHash(), OwnerUserStatus.valueOf(e.getStatus()));
    }
}
