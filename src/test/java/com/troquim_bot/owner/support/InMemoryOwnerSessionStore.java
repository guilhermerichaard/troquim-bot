package com.troquim_bot.owner.support;

import com.troquim_bot.owner.application.OwnerSession;
import com.troquim_bot.owner.application.OwnerSessionStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOwnerSessionStore implements OwnerSessionStore {
    private final Map<String, OwnerSession> byHash = new ConcurrentHashMap<>();

    @Override
    public OwnerSession criar(OwnerSession session) {
        byHash.put(session.tokenHash(), session);
        return session;
    }

    @Override
    public Optional<OwnerSession> buscarPorTokenHash(String tokenHash) {
        return Optional.ofNullable(byHash.get(tokenHash));
    }

    @Override
    public void revogarPorTokenHash(String tokenHash) {
        byHash.remove(tokenHash);
    }

    public int total() { return byHash.size(); }
}
