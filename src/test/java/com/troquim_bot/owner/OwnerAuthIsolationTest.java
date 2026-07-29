package com.troquim_bot.owner;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.owner.application.OwnerAuthService;
import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.infrastructure.BCryptPasswordHasher;
import com.troquim_bot.owner.support.InMemoryOwnerSessionStore;
import com.troquim_bot.owner.support.InMemoryOwnerUserRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Autenticação e isolamento de tenant entre donos.
 *
 * A garantia central: a sessão de um dono do negócio A resolve SEMPRE o businessId de A
 * — nunca o de B, nunca um tenant fixo. É essa identidade que qualquer rota do /app usa
 * no lugar do PilotTenantProvider.
 */
@DisplayName("OwnerAuthService - autenticacao e isolamento")
class OwnerAuthIsolationTest {

    private InMemoryOwnerUserRepository users;
    private InMemoryOwnerSessionStore sessions;
    private OwnerAuthService auth;
    private BCryptPasswordHasher hasher;

    @BeforeEach
    void setUp() {
        users = new InMemoryOwnerUserRepository();
        sessions = new InMemoryOwnerSessionStore();
        hasher = new BCryptPasswordHasher();
        auth = new OwnerAuthService(users, sessions, hasher);
    }

    private void criarDono(String email, String senha, com.troquim_bot.business.BusinessId tenant) {
        users.salvar(OwnerUser.novo(tenant, email, hasher.hash(senha)));
    }

    @Test
    @DisplayName("login valido emite sessao que resolve o businessId correto")
    void loginResolveOBusinessIdCorreto() {
        criarDono("dono-a@teste.com", "senha-forte-123", TestTenants.PILOT);

        Optional<String> token = auth.autenticar("dono-a@teste.com", "senha-forte-123");
        assertTrue(token.isPresent());

        AuthenticatedOwner identidade = auth.resolver(token.get()).orElseThrow();
        assertEquals(TestTenants.PILOT, identidade.businessId());
    }

    @Test
    @DisplayName("sessao do dono A nunca resolve o tenant B")
    void sessaoNaoAtravessaTenant() {
        criarDono("dono-a@teste.com", "senha-a-123456", TestTenants.PILOT);
        criarDono("dono-b@teste.com", "senha-b-123456", TestTenants.OUTRO);

        String tokenA = auth.autenticar("dono-a@teste.com", "senha-a-123456").orElseThrow();
        String tokenB = auth.autenticar("dono-b@teste.com", "senha-b-123456").orElseThrow();

        AuthenticatedOwner identidadeA = auth.resolver(tokenA).orElseThrow();
        AuthenticatedOwner identidadeB = auth.resolver(tokenB).orElseThrow();

        assertEquals(TestTenants.PILOT, identidadeA.businessId());
        assertEquals(TestTenants.OUTRO, identidadeB.businessId());
        assertNotEquals(identidadeA.businessId(), identidadeB.businessId());
        assertNotEquals(identidadeA.ownerId(), identidadeB.ownerId());
    }

    @Test
    @DisplayName("senha errada nao autentica")
    void senhaErradaNaoAutentica() {
        criarDono("dono@teste.com", "senha-correta-1", TestTenants.PILOT);
        assertTrue(auth.autenticar("dono@teste.com", "senha-errada-2").isEmpty());
    }

    @Test
    @DisplayName("e-mail inexistente e senha errada tem o MESMO resultado (sem side-channel)")
    void emailInexistenteMesmoResultado() {
        criarDono("existe@teste.com", "senha-correta-1", TestTenants.PILOT);

        Optional<String> emailFalso = auth.autenticar("nao-existe@teste.com", "qualquer-senha");
        Optional<String> senhaErrada = auth.autenticar("existe@teste.com", "senha-errada-x");

        assertTrue(emailFalso.isEmpty());
        assertTrue(senhaErrada.isEmpty());
    }

    @Test
    @DisplayName("token invalido ou inexistente nao resolve identidade")
    void tokenInvalidoNaoResolve() {
        assertTrue(auth.resolver("token-que-nunca-existiu").isEmpty());
        assertTrue(auth.resolver(null).isEmpty());
        assertTrue(auth.resolver("").isEmpty());
    }

    @Test
    @DisplayName("logout revoga a sessao; token usado depois nao resolve mais")
    void logoutRevogaSessao() {
        criarDono("dono@teste.com", "senha-correta-1", TestTenants.PILOT);
        String token = auth.autenticar("dono@teste.com", "senha-correta-1").orElseThrow();
        assertTrue(auth.resolver(token).isPresent());

        auth.encerrar(token);

        assertTrue(auth.resolver(token).isEmpty(), "Sessao revogada nao pode continuar valida");
    }

    @Test
    @DisplayName("logout de token desconhecido e idempotente, nao lanca")
    void logoutIdempotente() {
        assertDoesNotThrow(() -> auth.encerrar("token-nunca-emitido"));
    }

    @Test
    @DisplayName("o token de sessao nunca e persistido em claro")
    void tokenNuncaEmClaroNoStore() {
        criarDono("dono@teste.com", "senha-correta-1", TestTenants.PILOT);
        String token = auth.autenticar("dono@teste.com", "senha-correta-1").orElseThrow();

        assertEquals(1, sessions.total());
        // A unica forma de "achar" a sessao e' pelo hash, nao pelo valor cru:
        assertTrue(sessions.buscarPorTokenHash(token).isEmpty(),
                "O valor em claro do token nao pode ser a chave de armazenamento");
    }
}
