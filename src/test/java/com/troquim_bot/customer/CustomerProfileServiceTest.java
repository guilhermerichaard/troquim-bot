package com.troquim_bot.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerProfileServiceTest {

    private final CustomerProfileService service = new CustomerProfileService();

    @Test
    void criaPerfilAutomaticamenteESalvaNome() {
        CustomerProfile profile = service.buscarOuCriar("5511999999999");

        assertEquals("5511999999999", profile.getNumero());

        service.salvarNome("5511999999999", "Guilherme");

        CustomerProfile updatedProfile = service.localizarPorTelefone("5511999999999").orElseThrow();
        assertEquals("Guilherme", updatedProfile.getNome());
        assertEquals("Guilherme", service.nomePreferido(updatedProfile).orElseThrow());
    }

    @Test
    void usaApelidoComoNomePreferido() {
        CustomerProfile profile = service.buscarOuCriar("5511888888888");
        profile.setNome("Guilherme");
        profile.setApelido("Gui");

        assertEquals("Gui", service.nomePreferido(profile).orElseThrow());
    }

    @Test
    void incrementaAtendimentosAoIniciarAtendimento() {
        CustomerProfile profile = service.iniciarAtendimento("5511777777777");
        service.iniciarAtendimento("5511777777777");

        assertEquals(2, profile.getTotalAtendimentos());
        assertTrue(profile.getUltimoAtendimento() != null);
    }
}
