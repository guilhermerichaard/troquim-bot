package com.troquim_bot.common.valueobject;

import java.util.Objects;

/**
 * Value Object que representa um endereço.
 * Imutável, auto-validado e rico em comportamento.
 */
public class Address {

    private final String street;
    private final String number;
    private final String complement;
    private final String neighborhood;
    private final String city;
    private final String state;
    private final String zipCode;
    private final String country;

    public Address(String street, String number, String complement, String neighborhood, 
                   String city, String state, String zipCode, String country) {
        if (street == null || street.trim().isEmpty()) {
            throw new IllegalArgumentException("Rua é obrigatória");
        }
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalArgumentException("Número é obrigatório");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("Cidade é obrigatória");
        }
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("Estado é obrigatório");
        }
        if (zipCode == null || zipCode.trim().isEmpty()) {
            throw new IllegalArgumentException("CEP é obrigatório");
        }
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("País é obrigatório");
        }

        this.street = street.trim();
        this.number = number.trim();
        this.complement = complement != null ? complement.trim() : null;
        this.neighborhood = neighborhood != null ? neighborhood.trim() : null;
        this.city = city.trim();
        this.state = state.trim().toUpperCase();
        this.zipCode = zipCode.replaceAll("\\D", "");
        this.country = country.trim().toUpperCase();
    }

    /**
     * Cria Address brasileiro simplificado.
     */
    public static Address ofBrazilian(String street, String number, String neighborhood, 
                                      String city, String state, String zipCode) {
        return new Address(street, number, null, neighborhood, city, state, zipCode, "BRASIL");
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getCountry() {
        return country;
    }

    /**
     * Retorna o CEP formatado: 00000-000
     */
    public String getFormattedZipCode() {
        if (zipCode.length() == 8) {
            return zipCode.substring(0, 5) + "-" + zipCode.substring(5);
        }
        return zipCode;
    }

    /**
     * Retorna endereço completo formatado para exibição.
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street).append(", ").append(number);
        
        if (complement != null && !complement.isEmpty()) {
            sb.append(" - ").append(complement);
        }
        
        if (neighborhood != null && !neighborhood.isEmpty()) {
            sb.append(", ").append(neighborhood);
        }
        
        sb.append(", ").append(city).append(" - ").append(state);
        sb.append(", ").append(getFormattedZipCode());
        sb.append(", ").append(country);
        
        return sb.toString();
    }

    /**
     * Verifica se é um endereço brasileiro.
     */
    public boolean isBrazilian() {
        return "BRASIL".equals(country) || "BRAZIL".equals(country) || "BR".equals(country);
    }

    /**
     * Verifica se o CEP é válido (formato brasileiro: 8 dígitos).
     */
    public boolean hasValidZipCode() {
        return zipCode.matches("\\d{8}");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return street.equals(address.street) &&
               number.equals(address.number) &&
               Objects.equals(complement, address.complement) &&
               Objects.equals(neighborhood, address.neighborhood) &&
               city.equals(address.city) &&
               state.equals(address.state) &&
               zipCode.equals(address.zipCode) &&
               country.equals(address.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, number, complement, neighborhood, city, state, zipCode, country);
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}