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
    private final String hashParaContaInexistente;
    private final SecureRandom random = new SecureRandom();

    public OwnerAuthService(OwnerUserRepository ownerUserRepository, OwnerSessionStore sessionStore,
                            PasswordHasher passwordHasher) {
        this.ownerUserRepository = ownerUserRepository;
        this.sessionStore = sessionStore;
        this.passwordHasher = passwordHasher;
        this.hashParaContaInexistente = passwordHasher.hash(novoToken());
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
        // Executa o mesmo trabalho de hashing também para conta ausente/inativa.
        // A resposta genérica sozinha não evita enumeração por diferença de tempo.
        boolean senhaValida = passwordHasher.confere(senhaClara,
                owner.map(OwnerUser::getSenhaHash).orElse(hashParaContaInexistente));
        if (owner.isEmpty() || !owner.get().podeAutenticar() || !senhaValida) {
            log.info("Autenticacao de dono recusada");
            return Optional.empty();
        }

        return emitirSessao(owner.get());
    }

    /** Emite a mesma sessão canônica depois que qualquer método prova a identidade. */
    @Transactional
    public Optional<String> emitirSessao(OwnerUser owner) {
        if (owner == null || !owner.podeAutenticar()) return Optional.empty();
        Optional<OwnerUser> atual = ownerUserRepository.buscarPorId(owner.getId())
                .filter(OwnerUser::podeAutenticar)
                .filter(o -> o.pertenceAoTenant(owner.getBusinessId()));
        if (atual.isEmpty()) return Optional.empty();

        String tokenClaro = novoToken();
        LocalDateTime agora = LocalDateTime.now();
        sessionStore.criar(new OwnerSession(
                hash(tokenClaro), owner.getId(), owner.getBusinessId(),
                agora, agora.plusHours(SESSAO_TTL_HORAS)));
        log.info("Dono autenticado, sessao criada");
        return Optional.of(tokenClaro);
    }

    /** Emite a sessão canônica a partir do identificador provado por WebAuthn. */
    @Transactional
    public Optional<String> emitirSessaoPorOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return Optional.empty();
        try {
            var id = com.troquim_bot.owner.domain.OwnerUserId.from(java.util.UUID.fromString(ownerId));
            return ownerUserRepository.buscarPorId(id)
                    .filter(OwnerUser::podeAutenticar)
                    .flatMap(this::emitirSessao);
        } catch (IllegalArgumentException invalidId) {
            return Optional.empty();
        }
    }

    /** Resolve a identidade autenticada a partir do token em claro do cookie. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedOwner> resolver(String tokenClaro) {
        if (tokenClaro == null || tokenClaro.isBlank()) {
            return Optional.empty();
        }
        return sessionStore.buscarPorTokenHash(hash(tokenClaro))
                .filter(s -> s.utilizavel(LocalDateTime.now()))
                .filter(s -> ownerUserRepository.buscarPorId(s.ownerId())
                        .filter(OwnerUser::podeAutenticar)
                        .filter(owner -> owner.pertenceAoTenant(s.businessId()))
                        .isPresent())
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

    @Transactional(readOnly = true)
    public java.util.List<SessionView> listarSessoes(AuthenticatedOwner owner, String tokenAtual) {
        String atual = tokenAtual == null ? "" : hash(tokenAtual);
        return sessionStore.listarDoDono(owner.ownerId(), owner.businessId()).stream()
                .filter(s -> s.utilizavel(LocalDateTime.now()))
                .sorted(java.util.Comparator.comparing(OwnerSession::criadaEm).reversed())
                .map(s -> new SessionView(idPublico(s), s.criadaEm(), s.expiraEm(),
                        s.tokenHash().equals(atual))).toList();
    }

    @Transactional
    public void encerrarOutrasSessoes(AuthenticatedOwner owner, String tokenAtual) {
        if (tokenAtual == null || tokenAtual.isBlank()) throw new IllegalArgumentException("Sessão atual obrigatória");
        String atual = hash(tokenAtual);
        sessionStore.listarDoDono(owner.ownerId(), owner.businessId()).stream()
                .filter(s -> !s.tokenHash().equals(atual))
                .forEach(s -> sessionStore.revogarPorTokenHash(s.tokenHash()));
    }

    @Transactional
    public void encerrarSessao(AuthenticatedOwner owner, String idPublico) {
        sessionStore.listarDoDono(owner.ownerId(), owner.businessId()).stream()
                .filter(s -> idPublico(s).equals(idPublico))
                .forEach(s -> sessionStore.revogarPorTokenHash(s.tokenHash()));
    }

    // Não expõe nem o token nem sua chave de persistência ao navegador.
    private static String idPublico(OwnerSession s) {
        return hash("owner-session-view:" + s.tokenHash()).replace('+', '-').replace('/', '_').replace("=", "");
    }

    public record SessionView(String id, LocalDateTime createdAt, LocalDateTime expiresAt, boolean current) {}

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
