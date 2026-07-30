package com.troquim_bot.support;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Dias da semana para testes que percorrem o fluxo de agendamento.
 *
 * POR QUE ISTO EXISTE
 *
 * Um dia fixo no teste ("segunda") torna o resultado dependente do relógio. Os três
 * resolvedores de produção ({@code AvailabilityApplicationService.proximaDataPara},
 * {@code AppointmentBookingService.resolverData} e o do BookingApplicationService) usam
 * semântica "próximo dia igual ou HOJE": quando o dia escolhido calha de ser hoje, eles
 * resolvem para a data de hoje — e {@code horariosLivres} descarta os horários que já
 * passaram. Depois do último slot do dia a agenda fica vazia, o fluxo trava e o teste
 * falha pelo horário da execução, não por regra de negócio.
 *
 * Os nomes devolvidos são a forma CANÔNICA que {@code BookingQueryResponder.extrairDia}
 * produz ("terça", com acento), para servirem tanto de entrada quanto de valor esperado
 * nas asserções que citam o dia na resposta.
 *
 * Só devolve dia ÚTIL: sábado fecha às 13h e produziria uma lista de horários diferente,
 * quebrando asserções que esperam a grade completa (9h–17h). Domingo não tem agenda.
 *
 * NÃO usar quando o dia é o próprio OBJETO do teste — por exemplo, o cenário que verifica
 * que "sábado às 17h" é recusado por estar fora do expediente. Ali o dia é a regra sendo
 * exercitada, não um passo qualquer do fluxo.
 */
public final class TestDias {

    private TestDias() {}

    /** Primeiro dia útil ESTRITAMENTE futuro. Nunca é hoje. */
    public static String futuroComAgenda() {
        return nome(primeiroUtilAPartirDe(LocalDate.now().plusDays(1)));
    }

    /**
     * Um SEGUNDO dia útil futuro, garantidamente diferente de {@link #futuroComAgenda()}.
     * Para cenários que trocam de dia no meio da conversa.
     */
    public static String outroFuturoComAgenda() {
        LocalDate primeiro = primeiroUtilAPartirDe(LocalDate.now().plusDays(1));
        return nome(primeiroUtilAPartirDe(primeiro.plusDays(1)));
    }

    private static LocalDate primeiroUtilAPartirDe(LocalDate data) {
        while (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private static String nome(LocalDate data) {
        return switch (data.getDayOfWeek()) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY, SUNDAY -> throw new IllegalStateException("fim de semana nao e' dia util");
        };
    }
}
