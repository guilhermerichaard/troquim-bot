package com.troquim_bot.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A regra de onde um atendimento pode COMEÇAR.
 *
 * O caso central é o do enunciado da etapa: num período 09:00–12:00, um serviço de 1h30
 * tem o último candidato às 10:30 — porque 11:00 terminaria 12:30, meia hora depois de o
 * negócio fechar. É a diferença entre cortar pelo fim do atendimento e cortar pelo início.
 */
@DisplayName("PoliticaDeInicioDeSlot - passo de 15 minutos, corte pelo FIM")
class PoliticaDeInicioDeSlotTest {

    private static final PoliticaDeInicioDeSlot POLITICA = PoliticaDeInicioDeSlot.padrao();

    private static IntervaloDeHorario periodo(int hIni, int hFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, 0), LocalTime.of(hFim, 0));
    }

    @Test
    @DisplayName("o passo do MVP é de 15 minutos e vive no Domain, com nome")
    void passoDoMvp() {
        assertThat(POLITICA.passo()).isEqualTo(Duration.ofMinutes(15));
        assertThat(PoliticaDeInicioDeSlot.PASSO_MVP).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("09:00-12:00 com serviço de 1h30: último candidato às 10:30, nunca 11:00")
    void exemploCanonicoDaEtapa() {
        var candidatos = POLITICA.candidatos(periodo(9, 12), Duration.ofMinutes(90));

        assertThat(candidatos).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30), LocalTime.of(9, 45),
                LocalTime.of(10, 0), LocalTime.of(10, 15), LocalTime.of(10, 30));
        assertThat(candidatos).doesNotContain(LocalTime.of(11, 0),
                LocalTime.of(10, 45), LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("serviço de 30 minutos vai até 11:30 no mesmo período")
    void servicoCurtoVaiMaisLonge() {
        var candidatos = POLITICA.candidatos(periodo(9, 12), Duration.ofMinutes(30));

        assertThat(candidatos).endsWith(LocalTime.of(11, 30));
        assertThat(candidatos).doesNotContain(LocalTime.of(11, 45));
        assertThat(candidatos).hasSize(11);
    }

    @Test
    @DisplayName("serviço de 45 minutos usa 45 minutos, não uma hora arredondada")
    void servicoDe45Minutos() {
        var candidatos = POLITICA.candidatos(periodo(9, 12), Duration.ofMinutes(45));

        assertThat(candidatos).endsWith(LocalTime.of(11, 15));
        assertThat(candidatos).doesNotContain(LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("serviço de 1h40 usa a duração REAL: último início às 10:15")
    void servicoDeUmaHoraEQuarenta() {
        var candidatos = POLITICA.candidatos(periodo(9, 12), Duration.ofMinutes(100));

        // 10:15 termina 11:55 e cabe. 10:30 terminaria 12:10, depois do fechamento.
        // O passo é ancorado no início do período, então 10:20 nem chega a ser candidato.
        assertThat(candidatos).endsWith(LocalTime.of(10, 15));
        assertThat(candidatos).doesNotContain(LocalTime.of(10, 20), LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("os candidatos são ancorados no INÍCIO do período, não na hora cheia")
    void ancoradoNoInicioDoPeriodo() {
        var candidatos = POLITICA.candidatos(
                IntervaloDeHorario.de(LocalTime.of(9, 10), LocalTime.of(10, 10)),
                Duration.ofMinutes(30));

        // Um expediente que abre 09:10 não pode perder os dez primeiros minutos.
        assertThat(candidatos).containsExactly(
                LocalTime.of(9, 10), LocalTime.of(9, 25), LocalTime.of(9, 40));
    }

    @Test
    @DisplayName("duração que não cabe no período não gera candidato nenhum")
    void duracaoQueNaoCabe() {
        assertThat(POLITICA.candidatos(periodo(9, 10), Duration.ofMinutes(90))).isEmpty();
    }

    @Test
    @DisplayName("duração exatamente igual ao período gera um único candidato")
    void duracaoExata() {
        assertThat(POLITICA.candidatos(periodo(9, 10), Duration.ofHours(1)))
                .containsExactly(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("entradas inválidas devolvem lista vazia, sem estourar")
    void entradasInvalidas() {
        assertThat(POLITICA.candidatos(null, Duration.ofMinutes(30))).isEmpty();
        assertThat(POLITICA.candidatos(periodo(9, 12), null)).isEmpty();
        assertThat(POLITICA.candidatos(periodo(9, 12), Duration.ZERO)).isEmpty();
        assertThat(POLITICA.candidatos(periodo(9, 12), Duration.ofMinutes(-30))).isEmpty();
    }

    @Test
    @DisplayName("a política é um objeto: passo customizado é possível sem mexer em quem consome")
    void passoCustomizavel() {
        var deMeiaEmMeiaHora = PoliticaDeInicioDeSlot.comPasso(Duration.ofMinutes(30));

        assertThat(deMeiaEmMeiaHora.candidatos(periodo(9, 11), Duration.ofMinutes(30)))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30),
                        LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("passo zero ou negativo é recusado")
    void passoInvalido() {
        assertThatThrownBy(() -> PoliticaDeInicioDeSlot.comPasso(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoliticaDeInicioDeSlot.comPasso(Duration.ofMinutes(-15)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
