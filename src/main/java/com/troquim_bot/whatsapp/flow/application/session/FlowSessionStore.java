package com.troquim_bot.whatsapp.flow.application.session;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência das sessões do Flow. A Application define o contrato; a
 * Infrastructure escolhe o mecanismo (JPA hoje).
 */
public interface FlowSessionStore {

    /**
     * Abre uma sessão para um cliente. Chamado ANTES do envio da mensagem do Flow —
     * nunca a partir de dados recebidos do cliente. A sessão precisa existir antes,
     * senão uma resposta muito rápida da Meta chegaria a um token desconhecido.
     */
    FlowSession abrir(String telefone, UUID businessId, LocalDateTime expiraEm);

    /** Localiza a sessão pelo token. Vazio = token desconhecido (HTTP 427). */
    Optional<FlowSession> buscar(String flowToken);

    /**
     * Registra o desfecho do CONFIRM e conclui a sessão. Idempotente: se já houver
     * desfecho gravado, o existente é preservado e devolvido.
     */
    FlowConfirmationOutcome registrarConfirmacao(String flowToken, FlowConfirmationOutcome outcome);

    /**
     * Anula uma sessão que não pode mais ser usada — compensação de um envio que falhou.
     * Não apaga a linha: o histórico do token continua reconhecível.
     */
    void invalidar(String flowToken);
}
