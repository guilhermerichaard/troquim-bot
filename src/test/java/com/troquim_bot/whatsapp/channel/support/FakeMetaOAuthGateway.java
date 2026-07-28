package com.troquim_bot.whatsapp.channel.support;

import com.troquim_bot.whatsapp.channel.application.MetaOAuthGateway;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway falso da Meta. Permite exercitar o fluxo inteiro sem tocar a Graph API e,
 * principalmente, contar quantas vezes a troca foi tentada — é assim que se prova que
 * um state inválido falha ANTES de qualquer chamada externa.
 */
public class FakeMetaOAuthGateway implements MetaOAuthGateway {

    private final String token;
    private final boolean recusar;
    private final AtomicInteger chamadas = new AtomicInteger();

    public FakeMetaOAuthGateway(String token) {
        this(token, false);
    }

    private FakeMetaOAuthGateway(String token, boolean recusar) {
        this.token = token;
        this.recusar = recusar;
    }

    /** Gateway que sempre recusa o code, como a Meta faria com um code inválido. */
    public static FakeMetaOAuthGateway queRecusa() {
        return new FakeMetaOAuthGateway(null, true);
    }

    @Override
    public String trocarCodePorToken(String code) {
        chamadas.incrementAndGet();
        if (recusar) {
            throw new MetaOAuthException("troca de code recusada");
        }
        return token;
    }

    public int chamadas() {
        return chamadas.get();
    }
}
