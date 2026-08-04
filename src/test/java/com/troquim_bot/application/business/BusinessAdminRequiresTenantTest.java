package com.troquim_bot.application.business;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.BusinessRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova estrutural de que a administração de Business é SEMPRE tenant-scoped: nenhum método
 * público de {@link BusinessApplicationService} decide qual negócio sozinho, e
 * {@link BusinessRepository} não tem seleção global (findAll).
 */
@DisplayName("Administração de Business exige BusinessId explícito em toda operação")
class BusinessAdminRequiresTenantTest {

    @Test
    @DisplayName("todo método público de BusinessApplicationService recebe BusinessId")
    void todaOperacaoAdministrativaRecebeBusinessId() {
        Method[] metodos = BusinessApplicationService.class.getDeclaredMethods();

        for (Method metodo : metodos) {
            if (!Modifier.isPublic(metodo.getModifiers())) {
                continue;
            }
            boolean recebeBusinessId = Arrays.asList(metodo.getParameterTypes()).contains(BusinessId.class);
            assertThat(recebeBusinessId)
                    .as("método público '%s' precisa exigir BusinessId — nenhuma escolha implícita de tenant",
                            metodo.getName())
                    .isTrue();
        }

        // Confiança de que a checagem acima tem o que checar: a classe não pode ter ficado vazia.
        assertThat(Arrays.stream(metodos).anyMatch(m -> Modifier.isPublic(m.getModifiers()))).isTrue();
    }

    @Test
    @DisplayName("BusinessRepository não expõe findAll — sem seleção global de negócio")
    void businessRepositoryNaoTemFindAll() {
        boolean temFindAll = Arrays.stream(BusinessRepository.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("findAll"));

        assertThat(temFindAll)
                .as("BusinessRepository não pode expor findAll: toda consulta de produção precisa de BusinessId")
                .isFalse();
    }
}
