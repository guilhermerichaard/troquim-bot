package com.troquim_bot.owner.application;

import com.troquim_bot.owner.domain.OwnerUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Autenticação do dono e emissão/validação de sessão do /app.
 *
 * É este serviço — não o PilotTenantProvider — que resolve o tenant de qualquer rota
 * autenticada do /app: o businessId vem SEMPRE de {@link AuthenticatedOwner}, derivado
 * da sessão validada aqui.
 *
 * O token de sessão é opaco (256 bits) e persistido só como hash: o valor em claro
 * existe apenas na resposta do login (cookie) e nunca é gravado.
 */
@Service
public class OwnerAuthService {

    private static final Logger log = LoggerFactory.getLogger(OwnerAuthService.class);
    private static final int TOKEN_BYTES = 32;
    private static final int SESSAO_TTL_HORAS = 12;

    private final OwnerUserRepository ownerUserRepository;
    private final OwnerSessionStore sessionStore;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public OwnerAuthService(OwnerUserRepository ownerUserRepository, OwnerSessionStore sessionStore,
                            PasswordHasher passwordHasher) {
        this.ownerUserRepository = ownerUserRepository;
        this.sessionStore = sessionStore;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Autentica por e-mail+senha e emite um token de sessão em claro (só desta vez).
     *
     * Mesma resposta para "e-mail não existe" e "senha errada": timing e mensagem não
     * podem revelar qual dos dois casos ocorreu.
     */
    @Transactional
    public Optional<String> autenticar(String email, String senhaClara) {
        if (email == null || email.isBlank() || senhaClara == null || senhaClara.isBlank()) {
            return Optional.empty();
        }
        Optional<OwnerUser> owner = ownerUserRepository.buscarPorEmail(email.trim().toLowerCase());
        if (owner.isEmpty() || !owner.get().podeAutenticar()
                || !passwordHasher.confere(senhaClara, owner.get().getSenhaHash())) {
            log.info("Autenticacao de dono recusada");
            return Optional.empty();
        }

        String tokenClaro = novoToken();
        LocalDateTime agora = LocalDateTime.now();
        sessionStore.criar(new OwnerSession(
                hash(tokenClaro), owner.get().getId(), owner.get().getBusinessId(),
                agora, agora.plusHours(SESSAO_TTL_HORAS)));

        log.info("Dono autenticado, sessao criada");
        return Optional.of(tokenClaro);
    }

    /** Resolve a identidade autenticada a partir do token em claro do cookie. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedOwner> resolver(String tokenClaro) {
        if (tokenClaro == null || tokenClaro.isBlank()) {
            return Optional.empty();
        }
        return sessionStore.buscarPorTokenHash(hash(tokenClaro))
                .filter(s -> s.utilizavel(LocalDateTime.now()))
                .map(OwnerSession::comoIdentidade);
    }

    /** Logout: revoga a sessão. Idempotente — token desconhecido não é erro. */
    @Transactional
    public void encerrar(String tokenClaro) {
        if (tokenClaro == null || tokenClaro.isBlank()) {
            return;
        }
        sessionStore.revogarPorTokenHash(hash(tokenClaro));
    }

    private String novoToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 do token — não é senha (já tem 256 bits de entropia aleatória, sem
     * necessidade de custo computacional), só evita guardar o valor usável em claro.
     */
    private static String hash(String tokenClaro) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(tokenClaro.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
