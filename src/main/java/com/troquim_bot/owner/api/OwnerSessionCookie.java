package com.troquim_bot.owner.api;

import org.springframework.http.ResponseCookie;

/** Nome e construção do cookie de sessão do dono, compartilhados pelos fluxos de autenticação. */
public final class OwnerSessionCookie {
    public static final String NOME = "troquim_owner_session";
    public static final int MAX_AGE_SECONDS = 12 * 3600;

    private OwnerSessionCookie() {}

    public static ResponseCookie create(String value, int maxAgeSeconds) {
        return ResponseCookie.from(NOME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
