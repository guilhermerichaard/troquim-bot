package com.troquim_bot.whatsapp.channel.support;

import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStore;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store em memória para testes. Vive em src/test de propósito: não é um
 * {@code @Repository}, então nunca existe como bean e não há risco de a aplicação
 * gravar conexões num mapa volátil por engano.
 */
public class InMemoryChannelConnectionStore implements ChannelConnectionStore {

    private final Map<UUID, ChannelConnection> porId = new ConcurrentHashMap<>();

    @Override
    public ChannelConnection salvar(ChannelConnection conexao) {
        porId.put(conexao.id(), conexao);
        return conexao;
    }

    @Override
    public Optional<ChannelConnection> buscarPorTenant(UUID businessId) {
        return porId.values().stream()
                .filter(c -> c.pertenceAoTenant(businessId))
                .findFirst();
    }

    @Override
    public Optional<ChannelConnection> buscarPorState(String stateToken) {
        if (stateToken == null || stateToken.isBlank()) {
            return Optional.empty();
        }
        return porId.values().stream()
                .filter(c -> c.stateToken().map(stateToken::equals).orElse(false))
                .findFirst();
    }

    public int total() {
        return porId.size();
    }
}
