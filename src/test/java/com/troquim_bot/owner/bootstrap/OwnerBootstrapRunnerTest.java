package com.troquim_bot.owner.bootstrap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.domain.OwnerUserStatus;
import com.troquim_bot.owner.infrastructure.BCryptPasswordHasher;
import com.troquim_bot.owner.support.InMemoryOwnerUserRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provisionamento do primeiro owner via bootstrap idempotente por variável de ambiente.
 *
 * As garantias centrais: só age quando ligado; cria uma única vez; reexecutar nunca
 * duplica nem sobrescreve; e a senha em claro (e seu hash) NUNCA aparecem em log.
 */
@DisplayName("OwnerBootstrapRunner - provisionamento do primeiro owner")
class OwnerBootstrapRunnerTest {

    private static final String EMAIL = "dona.ana@teste.com";
    private static final String SENHA = "Senha-Temporaria-Forte-2026!";

    private InMemoryOwnerUserRepository users;
    private BCryptPasswordHasher hasher;
    private ListAppender<ILoggingEvent> logs;
    private Logger runnerLogger;

    @BeforeEach
    void setUp() {
        users = new InMemoryOwnerUserRepository();
        hasher = new BCryptPasswordHasher();

        runnerLogger = (Logger) LoggerFactory.getLogger(OwnerBootstrapRunner.class);
        logs = new ListAppender<>();
        logs.start();
        runnerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        runnerLogger.detachAppender(logs);
    }

    private OwnerBootstrapRunner runner(OwnerBootstrapProperties props) {
        return new OwnerBootstrapRunner(props, TestTenants.pilot(), users, hasher);
    }

    private OwnerBootstrapProperties props(boolean enabled, String email, String password) {
        OwnerBootstrapProperties p = new OwnerBootstrapProperties();
        p.setEnabled(enabled);
        p.setEmail(email);
        p.setPassword(password);
        return p;
    }

    @Test
    @DisplayName("desligado: nenhuma acao, mesmo com email e senha fornecidos")
    void desligadoNaoFazNada() {
        runner(props(false, EMAIL, SENHA)).run(null);

        assertFalse(users.existePorEmail(EMAIL), "Desligado nao pode criar owner");
        assertTrue(users.buscarPorEmail(EMAIL).isEmpty());
        assertTrue(logs.list.isEmpty(), "Desligado nao deve nem logar");
    }

    @Test
    @DisplayName("ligado + inexistente: cria owner ATIVO no negocio piloto com senha hasheada")
    void criaPrimeiroOwner() {
        runner(props(true, EMAIL, SENHA)).run(null);

        OwnerUser criado = users.buscarPorEmail(EMAIL).orElseThrow();
        assertEquals(EMAIL, criado.getEmail());
        assertEquals(TestTenants.PILOT, criado.getBusinessId(), "Deve vincular ao pilot-business-id");
        assertEquals(OwnerUserStatus.ATIVO, criado.getStatus());

        // A senha e guardada so como hash BCrypt: nunca em claro, e o hasher confere.
        assertNotEquals(SENHA, criado.getSenhaHash(), "A senha em claro nunca pode ser o hash");
        assertTrue(criado.getSenhaHash().startsWith("$2"), "Hash deve ser BCrypt");
        assertTrue(hasher.confere(SENHA, criado.getSenhaHash()));
    }

    @Test
    @DisplayName("ligado + email com caixa mista: normaliza para minusculas antes de criar")
    void normalizaEmail() {
        runner(props(true, "  Dona.ANA@Teste.com  ", SENHA)).run(null);

        assertTrue(users.buscarPorEmail(EMAIL).isPresent(),
                "E-mail deve ser normalizado (trim + lowercase)");
    }

    @Test
    @DisplayName("ligado + ja existe: no-op idempotente, nao duplica nem sobrescreve a senha")
    void idempotenteQuandoJaExiste() {
        String senhaOriginal = "Senha-Original-Do-Dono-999";
        OwnerUser existente = OwnerUser.novo(TestTenants.PILOT, EMAIL, hasher.hash(senhaOriginal));
        users.salvar(existente);

        // Bootstrap ligado com uma senha DIFERENTE nao pode reescrever o dono existente.
        runner(props(true, EMAIL, "Outra-Senha-Que-Nao-Deve-Valer-1")).run(null);

        OwnerUser depois = users.buscarPorEmail(EMAIL).orElseThrow();
        assertEquals(existente.getId(), depois.getId(), "Nao pode trocar o owner existente");
        assertTrue(hasher.confere(senhaOriginal, depois.getSenhaHash()),
                "A senha original deve continuar valendo");
        assertFalse(hasher.confere("Outra-Senha-Que-Nao-Deve-Valer-1", depois.getSenhaHash()),
                "A senha do bootstrap nao pode sobrescrever a existente");
    }

    @Test
    @DisplayName("ligado + email ausente: falha explicita no startup, nenhum owner criado")
    void configInvalidaEmailAusente() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner(props(true, "   ", SENHA)).run(null));

        assertTrue(ex.getMessage().contains("TROQUIM_OWNER_BOOTSTRAP_EMAIL"));
        assertFalse(users.existePorEmail(EMAIL));
    }

    @Test
    @DisplayName("ligado + senha ausente: falha explicita no startup, nenhum owner criado")
    void configInvalidaSenhaAusente() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runner(props(true, EMAIL, null)).run(null));

        assertTrue(ex.getMessage().contains("TROQUIM_OWNER_BOOTSTRAP_PASSWORD"));
        assertFalse(users.existePorEmail(EMAIL));
    }

    @Test
    @DisplayName("a senha em claro e o hash NUNCA aparecem em log (criacao e idempotencia)")
    void segredoNuncaEmLog() {
        // Caminho de criacao.
        runner(props(true, EMAIL, SENHA)).run(null);
        String hash = users.buscarPorEmail(EMAIL).orElseThrow().getSenhaHash();

        // Caminho idempotente (loga "ja existe").
        runner(props(true, EMAIL, SENHA)).run(null);

        List<ILoggingEvent> eventos = logs.list;
        assertFalse(eventos.isEmpty(), "Deve haver logs para provar que mesmo assim nao vazam");
        for (ILoggingEvent evento : eventos) {
            String linha = evento.getFormattedMessage();
            assertFalse(linha.contains(SENHA), "Log nao pode conter a senha em claro");
            assertFalse(linha.contains(hash), "Log nao pode conter o hash da senha");
        }
    }
}
