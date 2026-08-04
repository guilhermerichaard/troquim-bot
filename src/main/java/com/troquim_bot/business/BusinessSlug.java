package com.troquim_bot.business;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object do identificador público e exclusivo de um negócio na URL.
 *
 * ÚNICA implementação das regras de slug do sistema — nenhum outro lugar do código decide
 * o que é um slug válido nem como normalizar um candidato.
 *
 * Regras:
 * <ul>
 *   <li>somente minúsculas, letras ASCII, números e hífen;</li>
 *   <li>sem espaço, sem hífen no início/fim, sem hífens consecutivos;</li>
 *   <li>tamanho entre 3 e 63;</li>
 *   <li>reservados (ver {@link SlugReservadoPolicy}) são recusados.</li>
 * </ul>
 *
 * A unicidade GLOBAL do slug não é regra deste Value Object — um VO não enxerga outros
 * negócios. Ela é garantida pelo banco (constraint UNIQUE) e traduzida pela Application em
 * conflito controlado; ver {@code JpaBusinessPublicProfileRepository}.
 */
public final class BusinessSlug {

    private static final int TAMANHO_MINIMO = 3;
    private static final int TAMANHO_MAXIMO = 63;
    private static final Pattern FORMATO_VALIDO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final String valor;

    private BusinessSlug(String valor) {
        this.valor = valor;
    }

    /**
     * Normaliza um candidato bruto (ex.: digitado pelo dono, ou sugerido por IA no futuro) e
     * constrói o slug. NORMALIZAÇÃO DETERMINÍSTICA: mesmo texto de entrada sempre produz o
     * mesmo slug, em qualquer execução.
     *
     * <ol>
     *   <li>minúsculas;</li>
     *   <li>acentos removidos por decomposição Unicode (NFD) + descarte das marcas
     *       combinantes — "São Paulo" vira "sao paulo", nunca "s?o paulo";</li>
     *   <li>qualquer sequência de caracteres fora de [a-z0-9] vira UM hífen;</li>
     *   <li>hífens nas pontas são descartados.</li>
     * </ol>
     *
     * Se o resultado ainda assim não for um slug válido (vazio, curto demais, longo demais
     * ou reservado), a construção é recusada — normalizar não é sinônimo de sempre aceitar.
     */
    public static BusinessSlug normalizarDe(String bruto) {
        return validarEConstruir(normalizar(bruto));
    }

    /**
     * Aceita um valor JÁ no formato de slug, sem tentar corrigir nada — uso da infraestrutura
     * ao reconstituir a partir do banco, onde o valor já foi validado na escrita.
     */
    public static BusinessSlug de(String valor) {
        return validarEConstruir(valor);
    }

    private static String normalizar(String bruto) {
        if (bruto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(bruto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String comHifens = semAcento.replaceAll("[^a-z0-9]+", "-");
        return comHifens.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    private static BusinessSlug validarEConstruir(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Slug não pode ser vazio");
        }
        if (valor.length() < TAMANHO_MINIMO || valor.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "Slug deve ter entre " + TAMANHO_MINIMO + " e " + TAMANHO_MAXIMO
                            + " caracteres: '" + valor + "'");
        }
        if (!FORMATO_VALIDO.matcher(valor).matches()) {
            throw new IllegalArgumentException("Slug inválido: '" + valor + "'");
        }
        if (SlugReservadoPolicy.reservado(valor)) {
            throw new IllegalArgumentException("Slug reservado: '" + valor + "'");
        }
        return new BusinessSlug(valor);
    }

    public String getValue() {
        return valor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessSlug that = (BusinessSlug) o;
        return valor.equals(that.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
