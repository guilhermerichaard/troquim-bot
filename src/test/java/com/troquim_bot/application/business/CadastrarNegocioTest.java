package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CadastrarNegocio — o único caminho para criar a raiz de identidade do negócio.
 *
 * Entrada mínima é o BusinessId EXPLÍCITO: nada aqui deduz por nome nem gera id sozinho.
 */
@DisplayName("CadastrarNegocio - cadastro explícito e idempotente do negócio")
class CadastrarNegocioTest {

    private InMemoryBusinessRepository businessRepository;
    private CadastrarNegocio cadastrarNegocio;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        cadastrarNegocio = new CadastrarNegocio(businessRepository);
    }

    @Test
    @DisplayName("cadastra um negócio novo com os dados informados")
    void cadastraNegocioNovo() {
        BusinessId id = BusinessId.from(UUID.randomUUID());

        Business business = cadastrarNegocio.cadastrar(id, "Salão da Ana", "+5511999990000", "Rua A, 100");

        assertThat(business.getId()).isEqualTo(id);
        assertThat(business.getNome()).isEqualTo("Salão da Ana");
        assertThat(business.getTelefone()).isEqualTo("+5511999990000");
        assertThat(business.getEndereco()).isEqualTo("Rua A, 100");
        assertThat(businessRepository.exists(id)).isTrue();
    }

    @Test
    @DisplayName("contato pode estar incompleto: telefone e endereço nulos não bloqueiam o cadastro")
    void aceitaContatoIncompleto() {
        BusinessId id = BusinessId.from(UUID.randomUUID());

        Business business = cadastrarNegocio.cadastrar(id, "Negócio Recém-Cadastrado", null, null);

        assertThat(business.getTelefone()).isNull();
        assertThat(business.getEndereco()).isNull();
    }

    @Test
    @DisplayName("BusinessId é obrigatório: nada aqui gera id sozinho")
    void businessIdEhObrigatorio() {
        assertThatThrownBy(() -> cadastrarNegocio.cadastrar(null, "Nome", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BusinessId");
    }

    @Test
    @DisplayName("reexecutar com os MESMOS dados é idempotente: devolve o negócio já cadastrado")
    void reexecucaoComMesmosDadosEhIdempotente() {
        BusinessId id = BusinessId.from(UUID.randomUUID());

        Business primeira = cadastrarNegocio.cadastrar(id, "Salão da Ana", "+5511999990000", "Rua A, 100");
        Business segunda = cadastrarNegocio.cadastrar(id, "Salão da Ana", "+5511999990000", "Rua A, 100");

        assertThat(segunda.getId()).isEqualTo(primeira.getId());
        assertThat(businessRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("reexecutar com o MESMO id mas dados CONFLITANTES é recusado")
    void mesmoIdComDadosConflitantesEhRecusado() {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        cadastrarNegocio.cadastrar(id, "Salão da Ana", "+5511999990000", "Rua A, 100");

        assertThatThrownBy(() -> cadastrarNegocio.cadastrar(id, "Outro Nome Qualquer", "+5511999990000", "Rua A, 100"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(id.toString());

        // O cadastro original não foi sobrescrito pela tentativa conflitante.
        assertThat(businessRepository.findById(id).getNome()).isEqualTo("Salão da Ana");
    }

    @Test
    @DisplayName("nenhuma dedução por nome: dois ids diferentes com o mesmo nome são dois negócios")
    void nenhumaDeducaoPorNome() {
        BusinessId a = BusinessId.from(UUID.randomUUID());
        BusinessId b = BusinessId.from(UUID.randomUUID());

        cadastrarNegocio.cadastrar(a, "Salão Popular", null, null);
        cadastrarNegocio.cadastrar(b, "Salão Popular", null, null);

        assertThat(businessRepository.findAll()).hasSize(2);
    }
}
