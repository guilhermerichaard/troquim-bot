package com.troquim_bot.business;

import com.troquim_bot.availability.IntervaloDeHorario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O expediente como COMPOSIÇÃO DE PERÍODOS.
 *
 * Estes testes existem para travar exatamente o que o modelo antigo (uma abertura, um
 * fechamento, um conjunto de dias) NÃO conseguia representar: almoço, sábado diferente e
 * dia fechado. Se alguém reintroduzir a janela única, todos eles caem.
 */
@DisplayName("BusinessHours - expediente por períodos")
class ExpedienteSemanalTest {

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    /** Semana do enunciado: segunda com almoço, terça corrido, sábado curto, domingo fechado. */
    private static BusinessHours semanaDoEnunciado() {
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
        semana.put(DiaSemana.SEGUNDA, List.of(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0)));
        semana.put(DiaSemana.TERCA, List.of(periodo(10, 0, 19, 0)));
        semana.put(DiaSemana.SABADO, List.of(periodo(9, 0, 14, 0)));
        return BusinessHours.deSemana(semana);
    }

    @Nested
    @DisplayName("O que o modelo antigo não representava")
    class NovosCasos {

        @Test
        @DisplayName("SÁBADO pode ter horário diferente da segunda")
        void sabadoDiferenteDeSegunda() {
            BusinessHours expediente = semanaDoEnunciado();

            assertThat(expediente.periodosDe(DiaSemana.SABADO))
                    .containsExactly(periodo(9, 0, 14, 0));
            assertThat(expediente.periodosDe(DiaSemana.SEGUNDA))
                    .isNotEqualTo(expediente.periodosDe(DiaSemana.SABADO));
            // Sábado fecha às 14:00, segunda às 18:00.
            assertThat(expediente.periodosDe(DiaSemana.SABADO).get(0).fim())
                    .isEqualTo(LocalTime.of(14, 0));
        }

        @Test
        @DisplayName("DOIS PERÍODOS no mesmo dia são o intervalo de almoço")
        void doisPeriodosNoMesmoDiaSaoOAlmoco() {
            BusinessHours expediente = semanaDoEnunciado();

            List<IntervaloDeHorario> segunda = expediente.periodosDe(DiaSemana.SEGUNDA);
            assertThat(segunda).containsExactly(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0));

            // O buraco entre 12:00 e 13:00 é o almoço: o negócio NÃO está aberto ali.
            assertThat(expediente.estaAberto(DiaSemana.SEGUNDA, LocalTime.of(11, 0))).isTrue();
            assertThat(expediente.estaAberto(DiaSemana.SEGUNDA, LocalTime.of(12, 30))).isFalse();
            assertThat(expediente.estaAberto(DiaSemana.SEGUNDA, LocalTime.of(15, 0))).isTrue();
        }

        @Test
        @DisplayName("dia SEM período está fechado — ausência, não flag")
        void diaSemPeriodoEstaFechado() {
            BusinessHours expediente = semanaDoEnunciado();

            assertThat(expediente.fechadoEm(DiaSemana.DOMINGO)).isTrue();
            assertThat(expediente.periodosDe(DiaSemana.DOMINGO)).isEmpty();
            assertThat(expediente.isDiaFuncionamento(DiaSemana.DOMINGO)).isFalse();
            assertThat(expediente.estaAberto(DiaSemana.DOMINGO, LocalTime.of(10, 0))).isFalse();
        }

        @Test
        @DisplayName("dia declarado com lista VAZIA também é fechado, sem estado ambíguo")
        void listaVaziaTambemEhFechado() {
            Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
            semana.put(DiaSemana.SEGUNDA, List.of(periodo(9, 0, 18, 0)));
            semana.put(DiaSemana.DOMINGO, List.of());

            BusinessHours expediente = BusinessHours.deSemana(semana);

            assertThat(expediente.fechadoEm(DiaSemana.DOMINGO)).isTrue();
            assertThat(expediente.getDiasFuncionamento()).containsExactly(DiaSemana.SEGUNDA);
        }
    }

    @Nested
    @DisplayName("Invariantes do expediente")
    class Invariantes {

        @Test
        @DisplayName("períodos SOBREPOSTOS do mesmo negócio no mesmo dia são rejeitados")
        void periodosSobrepostosRejeitados() {
            Map<DiaSemana, List<IntervaloDeHorario>> semana = Map.of(
                    DiaSemana.SEGUNDA, List.of(periodo(9, 0, 13, 0), periodo(12, 0, 18, 0)));

            assertThatThrownBy(() -> BusinessHours.deSemana(semana))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sobrepostos");
        }

        @Test
        @DisplayName("períodos que apenas se ENCOSTAM são aceitos")
        void periodosEncostadosAceitos() {
            assertThatCode(() -> BusinessHours.deSemana(Map.of(
                    DiaSemana.SEGUNDA, List.of(periodo(9, 0, 12, 0), periodo(12, 0, 18, 0)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a mesma sobreposição em DIAS diferentes é legítima")
        void mesmoHorarioEmDiasDiferentes() {
            assertThatCode(() -> BusinessHours.deSemana(Map.of(
                    DiaSemana.SEGUNDA, List.of(periodo(9, 0, 18, 0)),
                    DiaSemana.TERCA, List.of(periodo(9, 0, 18, 0)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("os períodos de um dia saem em ordem cronológica, na ordem que entrarem")
        void periodosSaemOrdenados() {
            BusinessHours expediente = BusinessHours.deSemana(Map.of(
                    DiaSemana.SEGUNDA, List.of(periodo(13, 0, 18, 0), periodo(9, 0, 12, 0))));

            assertThat(expediente.periodosDe(DiaSemana.SEGUNDA))
                    .containsExactly(periodo(9, 0, 12, 0), periodo(13, 0, 18, 0));
        }
    }

    @Nested
    @DisplayName("Estado de não configurado")
    class NaoConfigurado {

        @Test
        @DisplayName("negócio sem expediente é estado EXPLÍCITO e observável")
        void expedienteAusenteEhExplicito() {
            BusinessHours vazio = BusinessHours.naoConfigurado();

            assertThat(vazio.naoTemExpediente()).isTrue();
            assertThat(vazio.getDiasFuncionamento()).isEmpty();
            for (DiaSemana dia : DiaSemana.values()) {
                assertThat(vazio.fechadoEm(dia)).isTrue();
            }
        }

        @Test
        @DisplayName("expediente com ao menos um período NÃO é 'não configurado'")
        void comPeriodoEstaConfigurado() {
            assertThat(semanaDoEnunciado().naoTemExpediente()).isFalse();
        }
    }

    @Nested
    @DisplayName("Compatibilidade com a API administrativa")
    class Compatibilidade {

        @Test
        @DisplayName("a janela única legada é um caso PARTICULAR do modelo por períodos")
        void janelaUnicaEhUmPeriodoPorDia() {
            BusinessHours legado = new BusinessHours(LocalTime.of(9, 0), LocalTime.of(18, 0),
                    Set.of(DiaSemana.SEGUNDA, DiaSemana.TERCA));

            assertThat(legado.periodosDe(DiaSemana.SEGUNDA)).containsExactly(periodo(9, 0, 18, 0));
            assertThat(legado.periodosDe(DiaSemana.TERCA)).containsExactly(periodo(9, 0, 18, 0));
            assertThat(legado.fechadoEm(DiaSemana.QUARTA)).isTrue();
        }

        @Test
        @DisplayName("abertura/fechamento são a projeção da SEMANA, para exibição apenas")
        void aberturaEFechamentoSaoProjecao() {
            BusinessHours expediente = semanaDoEnunciado();

            // Primeira abertura da semana e último fechamento — não descrevem o almoço.
            assertThat(expediente.getAbertura()).isEqualTo(LocalTime.of(9, 0));
            assertThat(expediente.getFechamento()).isEqualTo(LocalTime.of(19, 0));
        }

        @Test
        @DisplayName("a janela legada continua recusando abertura depois do fechamento")
        void janelaLegadaValidaOrdem() {
            assertThatThrownBy(() -> new BusinessHours(LocalTime.of(18, 0), LocalTime.of(9, 0),
                    Set.of(DiaSemana.SEGUNDA)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
