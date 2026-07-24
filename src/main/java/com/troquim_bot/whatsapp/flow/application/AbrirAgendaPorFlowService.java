package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.application.booking.AberturaDeAgenda;
import com.troquim_bot.application.booking.AberturaDeAgendaResultado;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundFlowGateway;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.WhatsAppFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Caso de uso: abrir a agenda do cliente por WhatsApp Flow.
 *
 * Este é o elo que faltava entre a conversa e o Data Endpoint. A ordem das operações é a
 * parte importante:
 *
 * <ol>
 *   <li><b>identidade confiável</b> — o telefone vem de quem chama (a conversa, que o
 *       obteve do webhook autenticado da Meta) e o tenant do {@link TenantProvider}.
 *       Nenhum dos dois virá depois do payload do Flow;</li>
 *   <li><b>sessão antes do envio</b> — o token precisa existir no banco antes de a Meta
 *       poder usá-lo; criar depois abriria uma janela em que o cliente tocaria no botão e
 *       receberia 427;</li>
 *   <li><b>envio</b> — pela porta {@link OutboundFlowGateway}, não pela Graph API direto;</li>
 *   <li><b>compensação</b> — se o envio falhar, a sessão é INVALIDADA. Sem isso ficaria um
 *       token válido que ninguém recebeu, utilizável por quem o adivinhasse.</li>
 * </ol>
 *
 * A degradação é explícita e sem espalhar flags pelo chamador: {@link #disponivel()}
 * responde se vale a pena tentar, e o resultado diz o que aconteceu. Recurso desligado,
 * Flow não configurado ou canal sem suporte devolvem status próprio — nunca exceção.
 */
@Service
@ConditionalOnWhatsAppFlow
public class AbrirAgendaPorFlowService implements AberturaDeAgenda {

    private static final Logger log = LoggerFactory.getLogger(AbrirAgendaPorFlowService.class);

    private final FlowSessionStore sessionStore;
    private final TenantProvider tenantProvider;
    private final WhatsAppFlowProperties properties;
    /**
     * ObjectProvider e não injeção direta: o gateway só existe quando Cloud API e Flow
     * estão ambos ligados. Injetar direto tornaria o caso de uso incriável em ambientes
     * que legitimamente não têm o canal — exatamente o cenário de fallback.
     */
    private final ObjectProvider<OutboundFlowGateway> flowGateway;

    public AbrirAgendaPorFlowService(FlowSessionStore sessionStore,
                                     TenantProvider tenantProvider,
                                     WhatsAppFlowProperties properties,
                                     ObjectProvider<OutboundFlowGateway> flowGateway) {
        this.sessionStore = sessionStore;
        this.tenantProvider = tenantProvider;
        this.properties = properties;
        this.flowGateway = flowGateway;
    }

    /** Vale a pena tentar abrir a agenda por Flow neste ambiente? */
    @Override
    public boolean disponivel() {
        return properties.isEnabled()
                && properties.temFlowConfigurado()
                && flowGateway.getIfAvailable() != null;
    }

    /**
     * Abre a agenda para um cliente.
     *
     * @param telefone telefone E.164 do cliente, de origem confiável (webhook da Meta)
     */
    @Override
    public AberturaDeAgendaResultado abrirPara(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return AberturaDeAgendaResultado.indisponivel("telefone ausente");
        }
        if (!properties.temFlowConfigurado()) {
            return AberturaDeAgendaResultado.indisponivel("flow-id/flow-name não configurado");
        }

        OutboundFlowGateway gateway = flowGateway.getIfAvailable();
        if (gateway == null) {
            return AberturaDeAgendaResultado.canalNaoSuporta();
        }

        UUID businessId = tenantProvider.currentBusinessId().getValue();
        LocalDateTime expiraEm = LocalDateTime.now().plusMinutes(properties.getSessaoTtlMinutos());

        // Sessão PRIMEIRO: o token tem de existir antes de a Meta poder devolvê-lo.
        FlowSession sessao = sessionStore.abrir(telefone, businessId, expiraEm);

        try {
            gateway.sendFlow(telefone, new FlowMessage(
                    properties.getFlowId(),
                    properties.getFlowName(),
                    sessao.flowToken(),
                    properties.getCta(),
                    properties.getMensagem(),
                    properties.isModoRascunho()));
        } catch (RuntimeException falha) {
            // Compensação: um token que ninguém recebeu não pode continuar valendo.
            sessionStore.invalidar(sessao.flowToken());
            log.warn("Falha ao enviar mensagem de WhatsApp Flow; sessão invalidada ({}).",
                    falha.getClass().getSimpleName());
            return AberturaDeAgendaResultado.falhaNoEnvio(falha.getClass().getSimpleName());
        }

        log.info("Agenda aberta por WhatsApp Flow (sessão criada e mensagem enviada).");
        return AberturaDeAgendaResultado.enviado();
    }
}
