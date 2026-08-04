package com.troquim_bot.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova estrutural de que {@link BusinessCalendar} é a ÚNICA autoridade sobre o expediente:
 * {@link Business} não guarda BusinessHours nem expõe operação de calendário, e a porta
 * antiga (BusinessHoursRepository) não existe mais no código.
 */
@DisplayName("Business não é autoridade sobre calendário — BusinessCalendar é")
class BusinessCalendarOwnershipTest {

    @Test
    @DisplayName("Business não contém nenhum campo BusinessHours")
    void businessNaoTemCampoBusinessHours() {
        for (Field campo : Business.class.getDeclaredFields()) {
            assertThat(campo.getType())
                    .as("campo '%s' de Business não pode ser BusinessHours", campo.getName())
                    .isNotEqualTo(BusinessHours.class);
        }
    }

    @Test
    @DisplayName("Business não expõe nenhum método que fale de BusinessHours ou de horário de funcionamento")
    void businessNaoExpoeOperacaoDeCalendario() {
        for (Method metodo : Business.class.getDeclaredMethods()) {
            assertThat(metodo.getReturnType())
                    .as("método '%s' não pode devolver BusinessHours", metodo.getName())
                    .isNotEqualTo(BusinessHours.class);
            for (Class<?> parametro : metodo.getParameterTypes()) {
                assertThat(parametro)
                        .as("método '%s' não pode receber BusinessHours", metodo.getName())
                        .isNotEqualTo(BusinessHours.class);
            }
            assertThat(metodo.getName())
                    .as("método '%s' parece regra de calendário, que não pertence a Business", metodo.getName())
                    .doesNotContainIgnoringCase("horarioFuncionamento")
                    .doesNotContainIgnoringCase("estaEmHorario");
        }
    }

    @Test
    @DisplayName("BusinessHoursRepository não existe mais no código — foi substituído por BusinessCalendarRepository")
    void businessHoursRepositoryNaoExisteMais() {
        assertThat(catchClassNotFound("com.troquim_bot.repository.BusinessHoursRepository")).isTrue();
        assertThat(catchClassNotFound("com.troquim_bot.repository.InMemoryBusinessHoursRepository")).isTrue();
        assertThat(catchClassNotFound(
                "com.troquim_bot.infrastructure.persistence.JpaBusinessHoursRepository")).isTrue();
    }

    @Test
    @DisplayName("BusinessCalendar é quem guarda o expediente, com identidade de BusinessId")
    void businessCalendarEhAAutoridade() throws NoSuchFieldException {
        Field expediente = BusinessCalendar.class.getDeclaredField("expediente");
        assertThat(expediente.getType()).isEqualTo(BusinessHours.class);

        Field businessId = BusinessCalendar.class.getDeclaredField("businessId");
        assertThat(businessId.getType()).isEqualTo(BusinessId.class);
    }

    private static boolean catchClassNotFound(String nomeClasse) {
        try {
            Class.forName(nomeClasse);
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }
}
