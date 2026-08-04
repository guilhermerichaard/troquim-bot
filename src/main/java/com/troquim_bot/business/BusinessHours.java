package com.troquim_bot.business;

import com.troquim_bot.availability.IntervaloDeHorario;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Expediente semanal do negócio: os períodos em que ele funciona, por dia.
 *
 * EVOLUÇÃO DELIBERADA. Antes esta classe era uma abertura, um fechamento e um conjunto de
 * dias — modelo que simplesmente NÃO representa a realidade de um salão: não expressa
 * intervalo de almoço, não expressa sábado com horário diferente e não expressa dois
 * períodos no mesmo dia. Qualquer um desses casos exigiria um booleano de remendo.
 *
 * Agora o expediente é COMPOSIÇÃO POR PERÍODOS: cada dia tem zero, um ou vários
 * {@link IntervaloDeHorario}. Segunda 09:00–12:00 + 13:00–18:00 é o almoço. Sábado
 * 09:00–14:00 é o sábado diferente. Domingo sem período é fechado — ausência, não flag.
 *
 * Value Object imutável e sem qualquer anotação de persistência: quem traduz isto em linhas
 * é o adapter JPA. Os períodos de um mesmo dia NÃO podem se sobrepor; a validação é do
 * agregado, não de quem chama.
 */
public class BusinessHours {

    private final Map<DiaSemana, List<IntervaloDeHorario>> periodosPorDia;

    /**
     * Expediente a partir dos períodos de cada dia. Dia ausente (ou com lista vazia) é dia
     * FECHADO — a ausência é a representação, e não há flag alternativa.
     */
    public static BusinessHours deSemana(Map<DiaSemana, List<IntervaloDeHorario>> periodosPorDia) {
        return new BusinessHours(periodosPorDia);
    }

    /** Negócio que ainda não configurou expediente. Estado observável, não erro. */
    public static BusinessHours naoConfigurado() {
        return new BusinessHours(Map.of());
    }

    /**
     * Forma LEGADA: uma janela única replicada nos dias informados.
     *
     * Preservada porque a API administrativa de Business ainda fala nesse vocabulário. Ela
     * é um caso PARTICULAR do modelo por períodos (um período por dia), não um modelo
     * paralelo — por isso delega, em vez de guardar campos próprios.
     */
    public BusinessHours(LocalTime abertura, LocalTime fechamento, Set<DiaSemana> diasFuncionamento) {
        this(porDia(abertura, fechamento, diasFuncionamento));
    }

    private BusinessHours(Map<DiaSemana, List<IntervaloDeHorario>> periodosPorDia) {
        Map<DiaSemana, List<IntervaloDeHorario>> normalizado = new EnumMap<>(DiaSemana.class);
        if (periodosPorDia != null) {
            for (Map.Entry<DiaSemana, List<IntervaloDeHorario>> entrada : periodosPorDia.entrySet()) {
                if (entrada.getKey() == null || entrada.getValue() == null || entrada.getValue().isEmpty()) {
                    continue;
                }
                List<IntervaloDeHorario> doDia = new ArrayList<>(entrada.getValue());
                doDia.forEach(periodo -> {
                    if (periodo == null) {
                        throw new IllegalArgumentException("Período nulo no expediente de " + entrada.getKey());
                    }
                });
                Collections.sort(doDia);
                recusarSobreposicao(entrada.getKey(), doDia);
                normalizado.put(entrada.getKey(), List.copyOf(doDia));
            }
        }
        this.periodosPorDia = Collections.unmodifiableMap(normalizado);
    }

    /**
     * Dois períodos sobrepostos no mesmo dia são erro de cadastro, não uma união implícita:
     * aceitar 09:00–13:00 junto de 12:00–18:00 faria o mesmo horário existir duas vezes e a
     * geração de slots produziria duplicatas.
     */
    private static void recusarSobreposicao(DiaSemana dia, List<IntervaloDeHorario> ordenados) {
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i - 1).sobrepoe(ordenados.get(i))) {
                throw new IllegalArgumentException("Períodos sobrepostos em " + dia + ": "
                        + ordenados.get(i - 1) + " e " + ordenados.get(i));
            }
        }
    }

    private static Map<DiaSemana, List<IntervaloDeHorario>> porDia(LocalTime abertura, LocalTime fechamento,
                                                                   Set<DiaSemana> dias) {
        if (abertura == null) {
            throw new IllegalArgumentException("Horário de abertura é obrigatório");
        }
        if (fechamento == null) {
            throw new IllegalArgumentException("Horário de fechamento é obrigatório");
        }
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("Deve ter pelo menos um dia de funcionamento");
        }
        if (!abertura.isBefore(fechamento)) {
            throw new IllegalArgumentException("Horário de abertura deve ser anterior ao fechamento");
        }
        IntervaloDeHorario janela = IntervaloDeHorario.de(abertura, fechamento);
        Map<DiaSemana, List<IntervaloDeHorario>> mapa = new EnumMap<>(DiaSemana.class);
        dias.forEach(dia -> mapa.put(dia, List.of(janela)));
        return mapa;
    }

    // ==================== CONSULTAS DO EXPEDIENTE ====================

    /** Períodos do dia, em ordem cronológica. Lista vazia = dia fechado. */
    public List<IntervaloDeHorario> periodosDe(DiaSemana dia) {
        return periodosPorDia.getOrDefault(dia, List.of());
    }

    /** Fechado é não ter nenhum período naquele dia. */
    public boolean fechadoEm(DiaSemana dia) {
        return periodosDe(dia).isEmpty();
    }

    /** Nenhum dia com período: o negócio ainda não publicou expediente. */
    public boolean naoTemExpediente() {
        return periodosPorDia.isEmpty();
    }

    public Map<DiaSemana, List<IntervaloDeHorario>> porDiaDaSemana() {
        return periodosPorDia;
    }

    public boolean isDiaFuncionamento(DiaSemana dia) {
        return !fechadoEm(dia);
    }

    public Set<DiaSemana> getDiasFuncionamento() {
        return new HashSet<>(periodosPorDia.keySet());
    }

    /**
     * Primeira abertura da semana. Projeção de APRESENTAÇÃO, para a API administrativa que
     * ainda exibe uma janela única — nunca use isto para decidir disponibilidade: um dia com
     * almoço tem buraco no meio que esta resposta não mostra.
     */
    public LocalTime getAbertura() {
        return periodosPorDia.values().stream()
                .flatMap(List::stream)
                .map(IntervaloDeHorario::inicio)
                .min(LocalTime::compareTo)
                .orElse(null);
    }

    /** Último fechamento da semana. Mesma ressalva de {@link #getAbertura()}. */
    public LocalTime getFechamento() {
        return periodosPorDia.values().stream()
                .flatMap(List::stream)
                .map(IntervaloDeHorario::fim)
                .max(LocalTime::compareTo)
                .orElse(null);
    }

    /** O negócio atende neste dia e horário? Considera cada período, e não a janela total. */
    public boolean estaAberto(DiaSemana dia, LocalTime horario) {
        if (horario == null) {
            return false;
        }
        return periodosDe(dia).stream()
                .anyMatch(p -> !horario.isBefore(p.inicio()) && !horario.isAfter(p.fim()));
    }

    /** Compatibilidade legada: aberto em QUALQUER dia naquele horário. */
    public boolean estaAberto(LocalTime horario) {
        if (horario == null) {
            return false;
        }
        for (DiaSemana dia : periodosPorDia.keySet()) {
            if (estaAberto(dia, horario)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessHours that = (BusinessHours) o;
        return periodosPorDia.equals(that.periodosPorDia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodosPorDia);
    }

    @Override
    public String toString() {
        return periodosPorDia.toString();
    }
}
