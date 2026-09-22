package com.troquim_bot.conversation;

import java.util.Optional;

/**
 * Porta de interpretação. Implementações podem ser heurísticas ou usar IA.
 *
 * Regra arquitetural: o interpretador nunca consulta agenda nem escolhe slot; ele só
 * transforma linguagem natural em preferência estruturada.
 */
public interface BookingIntentInterpreter {
    Optional<BookingIntent> interpretar(String mensagem);
}
