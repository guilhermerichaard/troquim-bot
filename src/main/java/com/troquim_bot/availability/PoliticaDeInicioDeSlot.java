package com.troquim_bot.availability;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Regra de DOMÍNIO sobre onde um atendimento pode começar.
 *
 * O passo de 15 minutos é decisão de negócio ("a cliente escolhe de quinze em quinze"), não
 * detalhe técnico. Por isso vive aqui, com nome, e não como um {@code 15} solto num handler,
 * num adapter ou numa linha de {@code application.properties} — onde ninguém encontraria a
 * regra ao procurar por ela, e onde cada caminho poderia adotar um passo diferente.
 *
 * A geração é sempre ANCORADA no início do período, não na hora cheia: um expediente que
 * abre 09:10 oferece 09:10, 09:25, 09:40 — e não pula os dez primeiros minutos.
 *
 * Customização por negócio está fora desta etapa, mas a forma já está pronta para ela: a
 * política é um objeto, então passar a carregá-la por tenant não muda quem a consome.
 */
public final class PoliticaDeInicioDeSlot {

    /** Passo do MVP: um candidato a cada 15 minutos dentro do período. */
    public static final Duration PASSO_MVP = Duration.ofMinutes(15);

    private static final PoliticaDeInicioDeSlot PADRAO = new PoliticaDeInicioDeSlot(PASSO_MVP);

    private final Duration passo;

    private PoliticaDeInicioDeSlot(Duration passo) {
        if (passo == null || passo.isZero() || passo.isNegative()) {
            throw new IllegalArgumentException("O passo entre inícios deve ser positivo");
        }
        this.passo = passo;
    }

    /** Política vigente do MVP. Única forma normal de obter a regra. */
    public static PoliticaDeInicioDeSlot padrao() {
        return PADRAO;
    }

    /** Política com passo explícito — existe para TESTE da própria regra. */
    public static PoliticaDeInicioDeSlot comPasso(Duration passo) {
        return new PoliticaDeInicioDeSlot(passo);
    }

    public Duration passo() {
        return passo;
    }

    /**
     * Inícios candidatos dentro de um período em que a duração INTEIRA cabe.
     *
     * O corte é pelo FIM do atendimento, nunca pelo início: num período 09:00–12:00, um
     * serviço de 1h30 tem o último candidato às 10:30, porque 11:00 terminaria 12:30 — meia
     * hora depois de o negócio fechar. Comparar apenas horários iniciais é justamente o erro
     * que faz o salão aceitar cliente para depois do fechamento.
     */
    public List<LocalTime> candidatos(IntervaloDeHorario periodo, Duration duracao) {
        List<LocalTime> inicios = new ArrayList<>();
        if (periodo == null || duracao == null || duracao.isZero() || duracao.isNegative()
                || !periodo.comporta(duracao)) {
            return List.of();
        }

        LocalTime candidato = periodo.inicio();
        while (true) {
            LocalTime terminaEm = candidato.plus(duracao);
            // Passou do fim do período — ou deu a volta no relógio (fim do dia).
            if (terminaEm.isAfter(periodo.fim()) || !candidato.isBefore(terminaEm)) {
                break;
            }
            inicios.add(candidato);

            LocalTime proximo = candidato.plus(passo);
            if (!proximo.isAfter(candidato)) {
                break; // virou o dia; não há mais candidatos neste período
            }
            candidato = proximo;
        }
        return List.copyOf(inicios);
    }
}
