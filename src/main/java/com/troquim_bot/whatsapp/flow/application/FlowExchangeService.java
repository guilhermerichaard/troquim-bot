package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.whatsapp.flow.application.handler.FlowActionHandler;
import com.troquim_bot.whatsapp.flow.application.handler.FlowHandlerRegistry;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Coordenador do Flow: decide O QUE fazer com uma requisição já decifrada.
 *
 * Trata os três casos de protocolo que não são agendamento ({@code ping}, notificação de
 * erro e {@code BACK}) e delega todo o resto ao handler da ação. Não conhece criptografia,
 * HTTP nem persistência de agendamento.
 *
 * O {@code ping} é respondido ANTES de qualquer resolução de sessão: é health check da
 * Meta e chega com token genérico — exigir sessão nele derrubaria a validação do endpoint
 * no painel da Meta.
 */
@Service
@ConditionalOnWhatsAppFlow
public class FlowExchangeService {

    private static final Logger log = LoggerFactory.getLogger(FlowExchangeService.class);

    private final FlowHandlerRegistry registry;
    private final FlowScreenPresenter presenter;
    private final FlowSessionStore sessionStore;
    private final FlowPreviewSessions previewSessions;
    private final TenantProvider tenantProvider;

    public FlowExchangeService(FlowHandlerRegistry registry, FlowScreenPresenter presenter,
                               FlowSessionStore sessionStore, FlowPreviewSessions previewSessions,
                               TenantProvider tenantProvider) {
        this.registry = registry;
        this.presenter = presenter;
        this.sessionStore = sessionStore;
        this.previewSessions = previewSessions;
        this.tenantProvider = tenantProvider;
    }

    public FlowExchangeOutcome processar(FlowRequest request) {
        if (request == null || request.action() == null) {
            return FlowExchangeOutcome.requisicaoInvalida();
        }

        if (request.isPing()) {
            return FlowExchangeOutcome.ok(FlowResponse.ativo());
        }

        // Notificação de erro do cliente: o protocolo pede apenas reconhecimento.
        // O conteúdo do erro não é logado (pode conter dado da tela).
        if (request.isNotificacaoDeErro()) {
            log.warn("WhatsApp Flow reportou erro de cliente na tela {}", request.screen());
            return FlowExchangeOutcome.ok(FlowResponse.reconhecido());
        }

        // A partir daqui, toda requisição precisa de uma sessão válida: é a única
        // amarração confiável entre o payload e o cliente do WhatsApp.
        LocalDateTime agora = LocalDateTime.now();
        final FlowSession session;
        if (previewSessions.ehTokenDePreview(request.flowToken())) {
            // Editor da Meta em rascunho: sessão efêmera, sem cliente e sem poder de
            // agendar. Deixa o Flow Builder testar a navegação sem cair no 427 — o CONFIRM
            // é simulado no handler, então não há brecha no agendamento real.
            session = previewSessions.novaSessao(request.flowToken(), agora);
        } else {
            Optional<FlowSession> sessao = sessionStore.buscar(request.flowToken());
            if (sessao.isEmpty() || !sessao.get().utilizavel(agora)
                    || !sessao.get().pertenceAoTenant(tenantProvider.currentBusinessId().getValue())) {
                // Token desconhecido, vencido, invalidado OU emitido para outro negócio:
                // todos falham igual, com 427 e sem pista sobre qual caso ocorreu (não
                // ajudar quem sonda tokens nem revelar que o token existe noutro tenant).
                return FlowExchangeOutcome.tokenInvalido();
            }
            session = sessao.get();
        }

        // INIT e BACK abrem/reabrem a tela inicial SERVICO com o profissional ainda
        // desabilitado (habilita após a escolha do serviço, como no exemplo oficial).
        // O contrato canônico não usa navegação por handler no BACK — a re-renderização
        // da entrada é suficiente para o Flow de 4 telas.
        if (request.isInit() || request.isBack()) {
            return FlowExchangeOutcome.ok(presenter.servico(false, null));
        }

        if (!request.isDataExchange()) {
            return FlowExchangeOutcome.requisicaoInvalida();
        }

        Optional<FlowActionHandler> handler = request.acaoDeNegocio().flatMap(registry::paraAcao);
        if (handler.isEmpty()) {
            // Evento desconhecido: recomeça pela tela inicial em vez de estourar 500.
            // Um Flow publicado desatualizado não deve derrubar o endpoint.
            log.warn("Evento desconhecido recebido do WhatsApp Flow na tela {}", request.screen());
            return FlowExchangeOutcome.ok(
                    presenter.servico(false, "Vamos recomeçar? Escolha o serviço desejado."));
        }

        return FlowExchangeOutcome.ok(handler.get().tratar(request, session));
    }
}
