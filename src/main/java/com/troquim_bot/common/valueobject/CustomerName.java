package com.troquim_bot.common.valueobject;

import java.util.Objects;

/**
 * Value Object que representa o nome de um cliente.
 * Imutável, auto-validado e rico em comportamento.
 */
public class CustomerName {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 100;

    private final String firstName;
    private final String lastName;

    public CustomerName(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("Primeiro nome é obrigatório");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sobrenome é obrigatório");
        }

        String trimmedFirst = firstName.trim();
        String trimmedLast = lastName.trim();

        if (trimmedFirst.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Primeiro nome deve ter pelo menos " + MIN_LENGTH + " caracteres");
        }
        if (trimmedLast.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Sobrenome deve ter pelo menos " + MIN_LENGTH + " caracteres");
        }
        if (trimmedFirst.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Primeiro nome não pode ter mais de " + MAX_LENGTH + " caracteres");
        }
        if (trimmedLast.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Sobrenome não pode ter mais de " + MAX_LENGTH + " caracteres");
        }

        this.firstName = capitalizeWords(trimmedFirst);
        this.lastName = capitalizeWords(trimmedLast);
    }

    /**
     * Cria CustomerName a partir de nome completo.
     * Separa no primeiro espaço encontrado.
     */
    public static CustomerName of(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome completo é obrigatório");
        }

        String[] parts = fullName.trim().split("\\s+", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : "";

        return new CustomerName(firstName, lastName);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /**
     * Retorna o nome completo.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Retorna as iniciais (ex: "João Silva" → "JS").
     */
    public String getInitials() {
        return String.valueOf(firstName.charAt(0)) + lastName.charAt(0);
    }

    /**
     * Verifica se o nome contém apenas letras e espaços.
     */
    public boolean hasOnlyLetters() {
        String fullName = getFullName().replace(" ", "");
        return fullName.chars().allMatch(Character::isLetter);
    }

    /**
     * Verifica se o nome é muito longo para exibição.
     */
    public boolean isLongForDisplay() {
        return getFullName().length() > 30;
    }

    /**
     * Retorna nome abreviado (primeiro nome + primeira letra do sobrenome).
     */
    public String getAbbreviated() {
        return firstName + " " + lastName.charAt(0) + ".";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerName that = (CustomerName) o;
        return firstName.equals(that.firstName) && lastName.equals(that.lastName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName);
    }

    @Override
    public String toString() {
        return getFullName();
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] words = text.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        
        return result.toString().trim();
    }
}