package com.troquim_bot.owner.support;

import com.troquim_bot.owner.application.OwnerUserRepository;
import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.domain.OwnerUserId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOwnerUserRepository implements OwnerUserRepository {
    private final Map<String, OwnerUser> byEmail = new ConcurrentHashMap<>();
    private final Map<OwnerUserId, OwnerUser> byId = new ConcurrentHashMap<>();

    @Override
    public OwnerUser salvar(OwnerUser owner) {
        byEmail.put(owner.getEmail(), owner);
        byId.put(owner.getId(), owner);
        return owner;
    }

    @Override
    public Optional<OwnerUser> buscarPorEmail(String email) {
        return Optional.ofNullable(byEmail.get(email == null ? null : email.trim().toLowerCase()));
    }

    @Override
    public Optional<OwnerUser> buscarPorId(OwnerUserId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existePorEmail(String email) {
        return byEmail.containsKey(email);
    }
}
