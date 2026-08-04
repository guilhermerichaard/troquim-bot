package com.troquim_bot.business;

import java.util.Locale;
import java.util.Set;

/**
 * ÚNICA lista de slugs reservados do sistema.
 *
 * Existe para que "este slug é reservado" tenha uma resposta, e uma só: espalhar esta lista
 * por Controllers, validações de formulário ou outros pontos criaria divergência garantida
 * assim que alguém esquecesse de atualizar uma das cópias.
 */
public final class SlugReservadoPolicy {

    private static final Set<String> RESERVADOS = Set.of(
            "api", "app", "admin", "login", "logout", "www", "health", "actuator",
            "webhook", "static", "assets", "robots", "favicon", "troquim"
    );

    private SlugReservadoPolicy() {
    }

    /** Comparação case-insensitive: o chamador não precisa normalizar antes de perguntar. */
    public static boolean reservado(String candidato) {
        return candidato != null && RESERVADOS.contains(candidato.toLowerCase(Locale.ROOT));
    }
}
