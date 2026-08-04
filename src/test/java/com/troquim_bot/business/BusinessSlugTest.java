package com.troquim_bot.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessSlug — a única implementação das regras de slug do sistema.
 */
@DisplayName("BusinessSlug - normalização determinística e validação")
class BusinessSlugTest {

    @Nested
    @DisplayName("normalização")
    class Normalizacao {

        @Test
        @DisplayName("slug já válido permanece igual")
        void slugValidoPermaneceIgual() {
            assertThat(BusinessSlug.normalizarDe("salao-da-ana").getValue()).isEqualTo("salao-da-ana");
        }

        @ParameterizedTest
        @CsvSource({
                "São Paulo, sao-paulo",
                "Salão da Ana, salao-da-ana",
                "Ação Beleza, acao-beleza",
                "Coração, coracao",
                "Múltiplos   Espaços, multiplos-espacos",
        })
        @DisplayName("acentos são removidos de forma determinística, e espaços viram hífen")
        void acentosRemovidosDeterministicamente(String bruto, String esperado) {
            assertThat(BusinessSlug.normalizarDe(bruto).getValue()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("mesmo texto de entrada sempre produz o mesmo slug")
        void normalizacaoEhDeterministica() {
            String a = BusinessSlug.normalizarDe("Salão da Ana").getValue();
            String b = BusinessSlug.normalizarDe("Salão da Ana").getValue();
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("maiúsculas e minúsculas normalizam para o mesmo slug (case-insensitive)")
        void maiusculasEMinusculasColidem() {
            BusinessSlug a = BusinessSlug.normalizarDe("MeuSalao");
            BusinessSlug b = BusinessSlug.normalizarDe("meusalao");
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("hífens no início, no fim e consecutivos são normalizados")
        void hifensNasPontasENoMeioSaoNormalizados() {
            assertThat(BusinessSlug.normalizarDe("--Salão---Ana--").getValue()).isEqualTo("salao-ana");
        }
    }

    @Nested
    @DisplayName("validação")
    class Validacao {

        @Test
        @DisplayName("slug vazio é recusado")
        void slugVazioEhRecusado() {
            assertThatThrownBy(() -> BusinessSlug.normalizarDe(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("slug nulo é recusado")
        void slugNuloEhRecusado() {
            assertThatThrownBy(() -> BusinessSlug.normalizarDe(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("candidato composto só de símbolos normaliza para vazio e é recusado")
        void candidatoSoDeSimbolosEhRecusado() {
            assertThatThrownBy(() -> BusinessSlug.normalizarDe("!!!@@@###"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "a"})
        @DisplayName("slug menor que 3 caracteres é recusado")
        void slugCurtoDemaisEhRecusado(String curto) {
            assertThatThrownBy(() -> BusinessSlug.de(curto))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("slug maior que 63 caracteres é recusado")
        void slugLongoDemaisEhRecusado() {
            String longo = "a".repeat(64);
            assertThatThrownBy(() -> BusinessSlug.de(longo))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("slug com exatamente 63 caracteres é aceito")
        void slugNoLimiteEhAceito() {
            String noLimite = "a".repeat(63);
            assertThat(BusinessSlug.de(noLimite).getValue()).hasSize(63);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Meu-Salao", "meu salao", "meu_salao", "meu.salao", "-meu-salao", "meu-salao-"})
        @DisplayName("valores fora do formato ([a-z0-9]+(-[a-z0-9]+)*) são recusados por BusinessSlug.de")
        void formatoInvalidoEhRecusado(String invalido) {
            assertThatThrownBy(() -> BusinessSlug.de(invalido))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("slugs reservados")
    class Reservados {

        @ParameterizedTest
        @ValueSource(strings = {
                "api", "app", "admin", "login", "logout", "www", "health", "actuator",
                "webhook", "static", "assets", "robots", "favicon", "troquim"
        })
        @DisplayName("slugs reservados são recusados")
        void slugsReservadosSaoRecusados(String reservado) {
            assertThatThrownBy(() -> BusinessSlug.normalizarDe(reservado))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservado");
        }

        @Test
        @DisplayName("reservado é recusado mesmo em outra caixa (case-insensitive)")
        void reservadoEmOutraCaixaTambemEhRecusado() {
            assertThatThrownBy(() -> BusinessSlug.normalizarDe("ADMIN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservado");
        }

        @Test
        @DisplayName("slug não reservado, mesmo parecido, é aceito")
        void slugParecidoComReservadoEhAceito() {
            assertThat(BusinessSlug.normalizarDe("minha-app").getValue()).isEqualTo("minha-app");
        }
    }
}
