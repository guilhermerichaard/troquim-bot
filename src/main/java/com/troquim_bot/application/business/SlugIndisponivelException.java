package com.troquim_bot.application.business;

/**
 * Sinaliza que um slug já pertence a OUTRO negócio.
 *
 * A autoridade sobre esta recusa é a constraint UNIQUE do banco, nunca um {@code exists}
 * prévio na Application — duas configurações concorrentes do mesmo slug podem passar juntas
 * por um {@code exists}, mas só uma sobrevive à escrita.
 */
public class SlugIndisponivelException extends RuntimeException {

    public SlugIndisponivelException(String slug, Throwable cause) {
        super("Slug indisponível (já em uso por outro negócio): '" + slug + "'", cause);
    }
}
