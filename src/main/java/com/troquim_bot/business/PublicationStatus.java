package com.troquim_bot.business;

/**
 * Estado de publicação do {@link BusinessPublicProfile}.
 *
 * Dois estados, nunca um boolean "publicado": um enum deixa explícito no código e no banco
 * o que cada estado significa, e abre espaço para um terceiro estado futuro (ex.: um dia
 * "em revisão") sem reinterpretar um campo que hoje é {@code true}/{@code false}.
 */
public enum PublicationStatus {
    DRAFT,
    PUBLISHED
}
