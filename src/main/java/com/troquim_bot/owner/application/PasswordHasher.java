package com.troquim_bot.owner.application;

/**
 * Porta de hashing de senha. A Application nunca conhece o algoritmo; a Infrastructure
 * escolhe (BCrypt hoje). O texto claro entra, o hash sai — e a comparação é feita aqui,
 * nunca por igualdade de string.
 */
public interface PasswordHasher {

    String hash(String senhaClara);

    boolean confere(String senhaClara, String hash);
}
