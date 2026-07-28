package com.troquim_bot.whatsapp.channel.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência das conexões de canal. Toda leitura é escopada por tenant —
 * não existe "buscar por id" solto, justamente para que nenhum caminho consiga
 * alcançar a conexão de outro Business por engano.
 */
public interface ChannelConnectionStore {

    /** Salva (cria ou atualiza) a conexão. */
    ChannelConnection salvar(ChannelConnection conexao);

    /** A conexão daquele tenant, se existir. */
    Optional<ChannelConnection> buscarPorTenant(UUID businessId);

    /**
     * Localiza pelo nonce do Embedded Signup.
     *
     * O nonce é a única pista que volta da Meta, então a busca por ele é inevitável —
     * mas o resultado ainda é confrontado com o tenant corrente pelo Application
     * Service antes de qualquer escrita.
     */
    Optional<ChannelConnection> buscarPorState(String stateToken);
}
