package com.troquim_bot.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O Value Object de período — a peça de que todo o expediente é composto.
 *
 * As bordas testadas aqui são as que produzem erro real na agenda: encostar não é sobrepor
 * (senão agendamentos consecutivos se bloqueariam), e "cabe a duração" é decidido pelo FIM
 * do atendimento (senão o salão aceita cliente para depois de fechar).
 */
@DisplayName("IntervaloDeHorario - período contínuo dentro de um dia")
class IntervaloDeHorarioTest {

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    @Nested
    @DisplayName("Invariantes")
    class Invariantes {

        @Test
        @DisplayName("início POSTERIOR ao fim é rejeitado")
        void inicioPosteriorAoFim() {
            assertThatThrownBy(() -> periodo(18, 0, 9, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anterior ao fim");
        }

        @Test
        @DisplayName("início IGUAL ao fim é rejeitado — período de duração zero não existe")
        void inicioIgualAoFim() {
            assertThatThrownBy(() -> periodo(9, 0, 9, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("anterior ao fim");
        }

        @Test
        @DisplayName("período atravessando a meia-noite é rejeitado EXPLICITAMENTE")
        void atravessarMeiaNoiteEhRejeitado() {
            // 22:00-02:00 é a forma clássica: o fim "menor" que o início denuncia a virada.
            assertThatThrownBy(() -> periodo(22, 0, 2, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meia-noite");
        }

        @Test
        @DisplayName("duração que estoura a meia-noite é rejeitada, e não dá a volta no relógio")
        void duracaoQueViraODiaEhRejeitada() {
            assertThatThrownBy(() -> IntervaloDeHorario.comDuracao(
                    LocalTime.of(23, 0), Duration.ofHours(2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meia-noite");
        }

        @Test
        @DisplayName("horários nulos são recusados")
        void nulosRecusados() {
            assertThatThrownBy(() -> IntervaloDeHorario.de(null, LocalTime.NOON))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> IntervaloDeHorario.de(LocalTime.NOON, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Sobreposição")
    class Sobreposicao {

        @Test
        @DisplayName("períodos que se cruzam sobrepõem")
        void cruzamento() {
            assertThat(periodo(9, 0, 13, 0).sobrepoe(periodo(12, 0, 18, 0))).isTrue();
            assertThat(periodo(12, 0, 18, 0).sobrepoe(periodo(9, 0, 13, 0))).isTrue();
        }

        @Test
        @DisplayName("período contido no outro sobrepõe")
        void contido() {
            assertThat(periodo(9, 0, 18, 0).sobrepoe(periodo(12, 0, 13, 0))).isTrue();
        }

        @Test
        @DisplayName("ENCOSTAR não é sobrepor — é o que permite atendimentos consecutivos")
        void encostarNaoSobrepoe() {
            assertThat(periodo(9, 0, 10, 0).sobrepoe(periodo(10, 0, 11, 0))).isFalse();
            assertThat(periodo(10, 0, 11, 0).sobrepoe(periodo(9, 0, 10, 0))).isFalse();
        }

        @Test
        @DisplayName("períodos separados não sobrepõem — manhã e tarde com almoço no meio")
        void separadosNaoSobrepoem() {
            assertThat(periodo(9, 0, 12, 0).sobrepoe(periodo(13, 0, 18, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("Encaixe de duração")
    class Encaixe {

        @Test
        @DisplayName("duração menor ou igual ao período cabe; maior não")
        void comporta() {
            IntervaloDeHorario manha = periodo(9, 0, 12, 0);

            assertThat(manha.comporta(Duration.ofMinutes(30))).isTrue();
            assertThat(manha.comporta(Duration.ofMinutes(45))).isTrue();
            assertThat(manha.comporta(Duration.ofHours(3))).isTrue();
            assertThat(manha.comporta(Duration.ofMinutes(181))).isFalse();
        }

        @Test
        @DisplayName("duração zero ou negativa nunca cabe")
        void duracaoInvalidaNaoCabe() {
            IntervaloDeHorario manha = periodo(9, 0, 12, 0);

            assertThat(manha.comporta(Duration.ZERO)).isFalse();
            assertThat(manha.comporta(Duration.ofMinutes(-30))).isFalse();
            assertThat(manha.comporta(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Interseção")
    class Intersecao {

        @Test
        @DisplayName("devolve a parte comum aos dois períodos")
        void parteComum() {
            assertThat(periodo(9, 0, 18, 0).intersecao(periodo(13, 0, 20, 0)))
                    .contains(periodo(13, 0, 18, 0));
        }

        @Test
        @DisplayName("períodos que só se encostam não têm interseção")
        void encostadosNaoIntersectam() {
            assertThat(periodo(9, 0, 12, 0).intersecao(periodo(12, 0, 18, 0))).isEmpty();
        }

        @Test
        @DisplayName("períodos disjuntos não têm interseção")
        void disjuntosNaoIntersectam() {
            assertThat(periodo(9, 0, 12, 0).intersecao(periodo(13, 0, 18, 0))).isEmpty();
        }
    }
}
