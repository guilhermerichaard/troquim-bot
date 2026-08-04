package com.troquim_bot.availability;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Value Object: um período contínuo dentro de UM dia.
 *
 * É a unidade de composição de todo o expediente. Um dia com almoço não é "um horário com
 * uma flag de intervalo": são DOIS períodos. Um dia fechado não é {@code aberto=false}: é
 * a AUSÊNCIA de período. Essa escolha elimina de uma vez a família de booleanos ambíguos
 * ({@code aberto}, {@code temIntervalo}, {@code horarioEspecial}, {@code usaHorarioPadrao})
 * que só existiriam para remendar um modelo de abertura/fechamento único.
 *
 * NÃO ATRAVESSA MEIA-NOITE no MVP. Um período 22:00–02:00 é recusado explicitamente em vez
 * de silenciosamente virar um intervalo vazio ou negativo: a aritmética de slots, conflito
 * e "cabe a duração" toda pressupõe início antes do fim no mesmo dia. Quando salão 24h
 * existir, isso vira dois períodos em dias distintos — decisão consciente, não acidente.
 */
public record IntervaloDeHorario(LocalTime inicio, LocalTime fim) implements Comparable<IntervaloDeHorario> {

    public IntervaloDeHorario {
        if (inicio == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (fim == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException(
                    "Período inválido: início (" + inicio + ") deve ser anterior ao fim (" + fim
                            + "). Períodos que atravessam a meia-noite não são aceitos no MVP.");
        }
    }

    public static IntervaloDeHorario de(LocalTime inicio, LocalTime fim) {
        return new IntervaloDeHorario(inicio, fim);
    }

    /** Período que começa em {@code inicio} e dura {@code duracao}. */
    public static IntervaloDeHorario comDuracao(LocalTime inicio, Duration duracao) {
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException("Duração deve ser positiva");
        }
        LocalTime fim = inicio.plus(duracao);
        // plus() no LocalTime dá a volta no relógio: 23:00 + 2h vira 01:00. Sem esta
        // checagem, um serviço longo perto do fim do dia produziria um período invertido.
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException(
                    "Duração de " + duracao + " a partir de " + inicio + " atravessa a meia-noite");
        }
        return new IntervaloDeHorario(inicio, fim);
    }

    public Duration duracao() {
        return Duration.between(inicio, fim);
    }

    /**
     * Sobreposição de períodos: {@code inicioA < fimB && inicioB < fimA}.
     *
     * Encostar NÃO é sobrepor: um período que termina exatamente onde o outro começa deixa
     * os dois válidos. É essa borda que permite agendar 09:00–10:00 e 10:00–11:00 em
     * sequência sem que um bloqueie o outro.
     */
    public boolean sobrepoe(IntervaloDeHorario outro) {
        return outro != null && inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    /** A duração inteira cabe dentro deste período? */
    public boolean comporta(Duration duracao) {
        return duracao != null && !duracao.isNegative() && !duracao.isZero()
                && duracao.compareTo(duracao()) <= 0;
    }

    /** O período todo (início E fim) está contido neste? */
    public boolean contem(IntervaloDeHorario outro) {
        return outro != null && !outro.inicio.isBefore(inicio) && !outro.fim.isAfter(fim);
    }

    /** Parte comum aos dois períodos. Vazio quando só se encostam ou nem isso. */
    public Optional<IntervaloDeHorario> intersecao(IntervaloDeHorario outro) {
        if (!sobrepoe(outro)) {
            return Optional.empty();
        }
        LocalTime maiorInicio = inicio.isAfter(outro.inicio) ? inicio : outro.inicio;
        LocalTime menorFim = fim.isBefore(outro.fim) ? fim : outro.fim;
        return Optional.of(new IntervaloDeHorario(maiorInicio, menorFim));
    }

    /** Ordem natural cronológica, para que listagens de expediente saiam legíveis. */
    @Override
    public int compareTo(IntervaloDeHorario outro) {
        int porInicio = inicio.compareTo(outro.inicio);
        return porInicio != 0 ? porInicio : fim.compareTo(outro.fim);
    }

    @Override
    public String toString() {
        return inicio + "-" + fim;
    }
}
