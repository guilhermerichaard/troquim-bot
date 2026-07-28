package com.troquim_bot.whatsapp.flow.application.session;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessão de um Flow aberto para um cliente específico.
 *
 * Existe porque o payload de {@code data_exchange} NÃO carrega o telefone do cliente:
 * a única amarração entre a requisição e a pessoa é o {@code flowToken}, gerado por nós
 * no momento em que a mensagem do Flow é enviada. Aceitar telefone ou businessId vindos
 * do {@code data} permitiria a qualquer um agendar em nome de terceiros, ou atravessar
 * tenants.
 *
 * A mesma linha é o registro de idempotência: {@code resultado} guarda o desfecho do
 * primeiro CONFIRM, de modo que uma reentrega devolva a mesma resposta sem duplicar
 * agendamento. Por isso a sessão concluída NÃO é apagada — sem ela não há como
 * reconhecer a repetição.
 *
 * @param flowToken  token opaco de 256 bits (nunca derivado do telefone)
 * @param telefone   telefone E.164 do cliente, resolvido no envio do Flow
 * @param businessId tenant dono da sessão
 * @param status     ver {@link FlowSessionStatus}
 * @param criadaEm   instante da criação
 * @param expiraEm   validade; vencida, o protocolo manda responder HTTP 427
 * @param resultado  desfecho do CONFIRM já executado; vazio enquanto não confirmado
 * @param preview    sessão efêmera do editor da Meta; ver {@link #preview}. Numa sessão
 *                   de preview não há cliente real e o CONFIRM é simulado — nunca agenda
 */
public record FlowSession(String flowToken,
                          String telefone,
                          UUID businessId,
                          FlowSessionStatus status,
                          LocalDateTime criadaEm,
                          LocalDateTime expiraEm,
                          Optional<FlowConfirmationOutcome> resultado,
                          boolean preview) {

    /** Sessão REAL: persistida e amarrada a um cliente e a um tenant. */
    public static FlowSession persistida(String flowToken, String telefone, UUID businessId,
                                         FlowSessionStatus status, LocalDateTime criadaEm,
                                         LocalDateTime expiraEm,
                                         Optional<FlowConfirmationOutcome> resultado) {
        return new FlowSession(flowToken, telefone, businessId, status, criadaEm, expiraEm,
                resultado, false);
    }

    /**
     * Sessão EFÊMERA de preview do editor da Meta. Não é persistida e, de propósito, não
     * tem telefone nem businessId: é a representação honesta de "não há cliente real por
     * trás deste token". Nasce ABERTA e válida pelo TTL informado, apenas para o Flow
     * Builder exercitar a navegação dinâmica. O handler de confirmação a reconhece por
     * {@link #preview()} e simula o sucesso — um token de preview NUNCA cria agendamento.
     */
    public static FlowSession preview(String flowToken, LocalDateTime criadaEm,
                                      LocalDateTime expiraEm) {
        return new FlowSession(flowToken, null, null, FlowSessionStatus.ABERTA, criadaEm,
                expiraEm, Optional.empty(), true);
    }

    public boolean expirada(LocalDateTime agora) {
        return expiraEm != null && agora.isAfter(expiraEm);
    }

    public boolean jaConfirmada() {
        return resultado.isPresent();
    }

    /**
     * A sessão pode processar uma troca agora?
     *
     * O vencimento é avaliado AQUI, na leitura, e não apenas por um status gravado: uma
     * sessão que venceu entre a gravação e esta chamada precisa falhar mesmo que nenhuma
     * rotina de limpeza tenha rodado.
     *
     * Sessão CONCLUIDA é intencionalmente utilizável: o CONFIRM idempotente precisa
     * alcançá-la para devolver o mesmo desfecho em vez de agendar de novo. Quem impede
     * o segundo agendamento é o desfecho gravado, não o bloqueio da leitura.
     */
    public boolean utilizavel(LocalDateTime agora) {
        if (expirada(agora)) {
            return false;
        }
        return status == FlowSessionStatus.ABERTA || status == FlowSessionStatus.CONCLUIDA;
    }

    /**
     * A sessão foi aberta para este tenant?
     *
     * Substitui o antigo {@code pertenceA(telefone, businessId)}: no Data Endpoint o
     * telefone NÃO é um fato independente — ele vem da própria sessão, então compará-lo
     * seria uma tautologia. O único fato externo verificável é o tenant corrente, e é
     * ele que impede uma sessão emitida sob outro negócio de ser reproduzida aqui.
     *
     * Preview não tem tenant por construção e é tratado antes desta verificação.
     */
    public boolean pertenceAoTenant(UUID businessId) {
        return businessId != null && businessId.equals(this.businessId);
    }
}
