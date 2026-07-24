package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIds;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.whatsapp.flow.application.FlowAction;
import com.troquim_bot.whatsapp.flow.application.FlowContexto;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolvido;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolver;
import com.troquim_bot.whatsapp.flow.application.FlowDataParser;
import com.troquim_bot.whatsapp.flow.application.FlowRequest;
import com.troquim_bot.whatsapp.flow.application.FlowResponse;
import com.troquim_bot.whatsapp.flow.application.FlowScreenPresenter;
import com.troquim_bot.whatsapp.flow.application.session.FlowConfirmationOutcome;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CONFIRMAR_AGENDAMENTO — footer da tela CONFIRMACAO. Único handler que ESCREVE.
 *
 * Ordem deliberada das defesas:
 * <ol>
 *   <li><b>revalidação</b> — a cadeia de escolhas é reconstruída (SEM checagem
 *       antecipada de disponibilidade: um retry encontraria o slot ocupado pelo próprio
 *       agendamento que criou);</li>
 *   <li><b>idempotência por COMANDO</b> — chave {@code flow_token + fingerprint do
 *       payload canônico}, reivindicada e concluída DENTRO da transação do agendamento
 *       ({@code booking_idempotency}). O token sozinho identificaria a sessão, não o
 *       comando;</li>
 *   <li><b>regra do MVP</b> — um flow_token conclui no máximo UM agendamento; escolha
 *       diferente após confirmar → {@code SESSAO_JA_CONFIRMADA};</li>
 *   <li><b>domínio decide</b> — conflito real detectado na mesma transação que grava;</li>
 *   <li><b>estado de apresentação</b> — a FlowSession registra o desfecho por último,
 *       fora do caminho crítico.</li>
 * </ol>
 *
 * A tela reservada SUCCESS só é retornada se a persistência concluiu. Qualquer falha
 * devolve o cliente a uma tela declarada com mensagem — nunca um sucesso falso.
 */
@Component
@ConditionalOnWhatsAppFlow
public class ConfirmarAgendamentoHandler implements FlowActionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarAgendamentoHandler.class);

    /**
     * Falha técnica: texto canônico compartilhado com a conversa. NEUTRO de propósito —
     * não afirma que o horário segue livre nem que nada foi criado; a nova tentativa é
     * segura pela idempotência por comando, não por a agenda estar inalterada.
     */
    private static final String MENSAGEM_FALHA_TECNICA = BookingResult.MENSAGEM_FALHA_TECNICA;

    /** Conflito: aqui SIM há evidência — existe agendamento sobreposto. */
    private static final String MENSAGEM_HORARIO_INDISPONIVEL =
            "Esse horário não está mais disponível. Escolha outro, por favor.";

    private final FlowContextoResolver resolver;
    private final FlowDataParser parser;
    private final FlowScreenPresenter presenter;
    private final BookingApplicationService bookingApplicationService;
    private final FlowSessionStore sessionStore;

    public ConfirmarAgendamentoHandler(FlowContextoResolver resolver, FlowDataParser parser,
                                       FlowScreenPresenter presenter,
                                       BookingApplicationService bookingApplicationService,
                                       FlowSessionStore sessionStore) {
        this.resolver = resolver;
        this.parser = parser;
        this.presenter = presenter;
        this.bookingApplicationService = bookingApplicationService;
        this.sessionStore = sessionStore;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.CONFIRMAR_AGENDAMENTO;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        // 1. Revalidação da cadeia, sem checagem antecipada de disponibilidade.
        FlowContextoResolvido resolvido = resolver.ateHorarioSemChecarDisponibilidade(request);
        if (!resolvido.valido()) {
            return resolvido.falha();
        }

        FlowContexto ctx = resolvido.contexto();
        String nome = parser.nome(request).orElse(null);
        if (nome == null) {
            return presenter.cliente(ctx, "", "Informe seu nome para continuar.");
        }

        // 2. Chave do comando. Base = flow_token; fingerprint = payload canônico com IDs
        // estáveis. businessId, telefone, serviceId e professionalId vêm da SESSÃO e do
        // catálogo — nunca do corpo enviado pelo cliente.
        BookingCommandKey chave = BookingCommandKey.de(
                session.flowToken(),
                session.businessId(),
                session.telefone(),
                BookingIds.serviceId(ctx.servico().id()),
                ctx.profissional().professionalId(),
                ctx.data(),
                ctx.horario());

        // 3. O domínio decide, na mesma transação em que grava.
        BookingResult resultado;
        try {
            resultado = bookingApplicationService.confirmarEm(
                    session.telefone(), nome, ctx.servico().id(),
                    ctx.profissional().professionalId(), ctx.data(), ctx.horario(),
                    ctx.servico().duracao(), chave);
        } catch (RuntimeException e) {
            // Transação desfeita por inteiro (inclusive a reivindicação). Só o tipo é
            // logado — nenhum texto de exceção é interpretado, nenhum payload registrado.
            log.error("Falha técnica ao confirmar agendamento via WhatsApp Flow: {}",
                    e.getClass().getSimpleName());
            return presenter.confirmacao(ctx, MENSAGEM_FALHA_TECNICA);
        }

        if (resultado.isSessaoJaConfirmada()) {
            // Regra do MVP: este Flow já rendeu um agendamento. Repetir AQUI nunca vai
            // funcionar — a mensagem pede um Flow novo, não outro horário.
            return presenter.confirmacao(ctx, BookingResult.MENSAGEM_SESSAO_JA_CONFIRMADA);
        }

        if (resultado.isFalhaTecnica()) {
            // Mantém o cliente em CONFIRMACAO com a MESMA escolha: repetir é retry do
            // mesmo comando, coberto pela idempotência.
            return presenter.confirmacao(ctx, MENSAGEM_FALHA_TECNICA);
        }

        if (resultado.isConflito()) {
            // Corrida REAL, observada: volta à AGENDA com os horários atualizados.
            return presenter.agenda(ctx, MENSAGEM_HORARIO_INDISPONIVEL);
        }

        if (!resultado.isConfirmado()) {
            return presenter.agenda(ctx, MENSAGEM_HORARIO_INDISPONIVEL);
        }

        // 4. Estado de APRESENTAÇÃO da sessão — não é o mecanismo de idempotência.
        // Falhar aqui não pode desfazer um agendamento já commitado.
        FlowConfirmationOutcome outcome = new FlowConfirmationOutcome(
                ctx.servico().titulo(), ctx.data().toString(), ctx.horario().toString());
        try {
            outcome = sessionStore.registrarConfirmacao(session.flowToken(), outcome);
        } catch (RuntimeException e) {
            log.warn("Não foi possível registrar o desfecho na sessão do Flow: {}",
                    e.getClass().getSimpleName());
        }

        return sucesso(session, outcome);
    }

    /**
     * Encerramento pela tela reservada SUCCESS. Os params voltam ao negócio no webhook
     * de conclusão (nfm_reply) e alimentam a mensagem final na conversa.
     */
    private FlowResponse sucesso(FlowSession session, FlowConfirmationOutcome outcome) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("servico", outcome.servicoNome());
        params.put("data", outcome.dataIso());
        params.put("horario", outcome.horario());
        return FlowResponse.terminal(session.flowToken(), params);
    }
}
