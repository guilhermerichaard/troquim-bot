package com.troquim_bot.whatsapp.channel.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso do vínculo "Conectar WhatsApp Business" (Embedded Signup da Meta).
 *
 * O tenant é SEMPRE recebido explícito, nunca resolvido aqui — mesma disciplina de
 * {@code AgendaDoNegocioService}: quem chama prova de qual negócio está falando. Isso
 * permite dois entrypoints legítimos sobre o MESMO serviço, sem duplicar regra: o
 * admin (Bearer, tenant do {@code TenantProvider}) e o dono autenticado em /app
 * (sessão, tenant da própria identidade) — nunca um tenant fixo por padrão.
 *
 * Divisão de responsabilidade: o navegador conduz o Embedded Signup e volta com um
 * {@code code} e o {@code state} que emitimos. A troca do code por token acontece
 * SÓ aqui, no servidor, porque exige o App Secret — que nunca sai da Infrastructure.
 * O frontend recebe apenas identificadores públicos.
 *
 * Coexistência (número que já pertence ao WhatsApp Business App) não é decisão nossa:
 * quem resolve isso é a Meta, dentro do próprio Embedded Signup. Por isso não existe
 * flag de coexistência neste serviço — o resultado chega pronto no vínculo.
 *
 * Nada é conectado automaticamente: sem uma finalização explícita, com nonce válido,
 * do MESMO dono que iniciou, e code aceito pela Meta, nenhum número é vinculado.
 */
@Service
public class ConectarWhatsAppChannelService {

    private static final Logger log = LoggerFactory.getLogger(ConectarWhatsAppChannelService.class);

    /** Janela para concluir o Embedded Signup. Curta: o nonce é de uso imediato. */
    private static final int STATE_TTL_MINUTOS = 15;

    /** 256 bits de entropia, como os tokens de sessão do Flow. Unicidade e
     * imprevisibilidade dão a mesma garantia de inforjabilidade de um HMAC, com a
     * vantagem de ser revogável no servidor e de não expor claims no próprio valor. */
    private static final int STATE_BYTES = 32;

    private final ChannelConnectionStore store;
    private final ChannelCredentialCipher cipher;
    private final MetaOAuthGateway oAuthGateway;
    private final SecureRandom random = new SecureRandom();

    public ConectarWhatsAppChannelService(ChannelConnectionStore store,
                                          ChannelCredentialCipher cipher,
                                          MetaOAuthGateway oAuthGateway) {
        this.store = store;
        this.cipher = cipher;
        this.oAuthGateway = oAuthGateway;
    }

    /**
     * Inicia o vínculo: emite o nonce que o frontend devolverá junto com o code.
     *
     * Reiniciar sobre um vínculo existente é permitido de propósito (reconectar é um
     * caso real), e reaproveita a mesma linha — o tenant continua com no máximo uma
     * conexão, e o nonce anterior deixa de valer no mesmo instante.
     *
     * @param ownerUserId dono que iniciou, quando houver identidade (vazio no caminho
     *                    administrativo); a finalização exigirá o mesmo dono.
     */
    @Transactional
    public IniciarConexaoResultado iniciar(UUID businessId, Optional<UUID> ownerUserId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId é obrigatório");
        }
        LocalDateTime agora = LocalDateTime.now();
        String state = novoState();

        UUID id = store.buscarPorTenant(businessId)
                .map(ChannelConnection::id)
                .orElseGet(UUID::randomUUID);

        ChannelConnection pendente = ChannelConnection.pendente(
                id, businessId, ownerUserId, state, agora.plusMinutes(STATE_TTL_MINUTOS), agora);
        store.salvar(pendente);

        log.info("Conexao de canal WhatsApp iniciada para o tenant {} (status={})",
                businessId, ChannelConnectionStatus.PENDENTE);

        return new IniciarConexaoResultado(state, ChannelConnectionStatus.PENDENTE);
    }

    /**
     * Finaliza o vínculo com o que voltou da Meta.
     *
     * Ordem deliberada: valida o nonce (tenant + dono + validade) ANTES de falar com a
     * Meta, para que um code avulso não gere sequer uma chamada externa. E o resultado
     * da Meta é cifrado ANTES de tocar o banco.
     *
     * @param ownerUserId dono que está finalizando; precisa ser o MESMO que iniciou
     *                    quando o início foi feito com identidade (vazio = caminho
     *                    administrativo, sem checagem de dono).
     */
    @Transactional
    public FinalizarConexaoResultado finalizar(UUID businessId, Optional<UUID> ownerUserId,
                                               String state, String code,
                                               String wabaId, String phoneNumberId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId é obrigatório");
        }
        LocalDateTime agora = LocalDateTime.now();

        ChannelConnection conexao = store.buscarPorState(state)
                .filter(c -> c.pertenceAoTenant(businessId))
                .filter(c -> c.stateUtilizavel(agora))
                .filter(c -> c.ownerUserId().isEmpty()
                        || ownerUserId.map(c::pertenceAoDono).orElse(false))
                .orElseThrow(() -> {
                    // Nonce desconhecido, vencido, já consumido, de outro tenant ou de
                    // outro dono: todos falham igual, sem dizer qual dos casos ocorreu.
                    log.warn("Finalizacao de conexao recusada para o tenant {}: state invalido",
                            businessId);
                    return new ConexaoInvalidaException("state invalido");
                });

        String tokenClaro;
        try {
            tokenClaro = oAuthGateway.trocarCodePorToken(code);
        } catch (RuntimeException e) {
            // Só o tipo é logado: mensagens de erro de OAuth podem ecoar o code.
            log.warn("Troca de code recusada para o tenant {}: {}",
                    businessId, e.getClass().getSimpleName());
            store.salvar(conexao.falhou("TROCA_DE_TOKEN_RECUSADA", agora));
            throw new ConexaoInvalidaException("nao foi possivel concluir a conexao");
        }

        EncryptedCredential credencial = cipher.cifrar(tokenClaro);
        ChannelConnection conectada = conexao.conectada(wabaId, phoneNumberId, credencial, agora);
        store.salvar(conectada);

        log.info("Canal WhatsApp conectado para o tenant {} (status={})",
                businessId, ChannelConnectionStatus.CONECTADO);

        return new FinalizarConexaoResultado(
                ChannelConnectionStatus.CONECTADO, conectada.wabaId(), conectada.phoneNumberId());
    }

    /** Status corrente do canal do tenant. Nunca devolve credencial. */
    @Transactional(readOnly = true)
    public Optional<ChannelConnection> consultar(UUID businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return store.buscarPorTenant(businessId);
    }

    /**
     * Revoga a conexão do tenant. Remove a linha por inteiro (não só a credencial):
     * depois de revogar, {@link #consultar} volta a "não conectado" — o mesmo estado
     * de um tenant que nunca conectou — e reconectar é um início limpo, sem herdar
     * WABA/phone number antigos.
     */
    @Transactional
    public void revogar(UUID businessId) {
        if (businessId == null) {
            return;
        }
        store.remover(businessId);
        log.info("Conexao de canal WhatsApp revogada para o tenant {}", businessId);
    }

    private String novoState() {
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O que o frontend precisa para abrir o Embedded Signup. Sem segredo. */
    public record IniciarConexaoResultado(String state, ChannelConnectionStatus status) {}

    /** Desfecho da finalização. Sem credencial, por construção. */
    public record FinalizarConexaoResultado(ChannelConnectionStatus status,
                                            Optional<String> wabaId,
                                            Optional<String> phoneNumberId) {}

    /**
     * Entrada recusada. A mensagem é genérica de propósito: distinguir "nonce
     * desconhecido" de "code recusado" ajudaria quem estivesse sondando.
     */
    public static class ConexaoInvalidaException extends RuntimeException {
        public ConexaoInvalidaException(String message) {
            super(message);
        }
    }
}
