package com.troquim_bot.common.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object que representa um número de telefone.
 * Imutável, auto-validado e rico em comportamento.
 */
public class PhoneNumber {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    
    private final String value;

    public PhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Número de telefone não pode ser vazio");
        }
        
        String cleaned = value.replaceAll("[\\s\\-\\(\\)]", "");
        
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("Formato de telefone inválido: " + value);
        }
        
        this.value = cleaned;
    }

    /**
     * Cria PhoneNumber a partir de número brasileiro (com DDD).
     * Aceita formatos: (11) 99999-9999, 11999999999, +5511999999999
     */
    public static PhoneNumber ofBrazilian(String ddd, String number) {
        if (ddd == null || number == null) {
            throw new IllegalArgumentException("DDD e número são obrigatórios");
        }
        
        String cleaned = ddd.replaceAll("\\D", "") + number.replaceAll("\\D", "");
        return new PhoneNumber("+" + cleaned);
    }

    public String getValue() {
        return value;
    }

    /**
     * Retorna a forma canônica E.164 do número: sempre com prefixo "+".
     *
     * Assume que os dígitos já incluem o código do país (caso dos números
     * vindos do WhatsApp, ex.: "5511999990001" → "+5511999990001"). NÃO infere
     * um DDI ausente — um número sem código de país vira apenas "+<digitos>",
     * e telefones inválidos são detectados/rejeitados na borda e na migração,
     * nunca "corrigidos" silenciosamente.
     */
    public String getE164() {
        return value.startsWith("+") ? value : "+" + value;
    }

    /**
     * Retorna o número formatado para exibição: (11) 99999-9999
     */
    public String getFormatted() {
        if (value.startsWith("+")) {
            return value;
        }
        
        if (value.length() == 11) {
            return String.format("(%s) %s-%s", 
                value.substring(0, 2),
                value.substring(2, 7),
                value.substring(7));
        }
        
        return value;
    }

    /**
     * Retorna apenas o DDD (código de área).
     */
    public String getDdd() {
        if (value.startsWith("+")) {
            return value.substring(3, 5);
        }
        return value.substring(0, 2);
    }

    /**
     * Verifica se é um número brasileiro.
     */
    public boolean isBrazilian() {
        return value.startsWith("+55") || (value.length() == 11 && !value.startsWith("+"));
    }

    /**
     * Verifica se é um número de celular (assumindo formato brasileiro).
     */
    public boolean isMobile() {
        if (!isBrazilian()) {
            return false;
        }
        
        String number = value.startsWith("+") ? value.substring(3) : value;
        if (number.length() == 11) {
            char thirdDigit = number.charAt(2);
            return thirdDigit == '9';
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneNumber that = (PhoneNumber) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}