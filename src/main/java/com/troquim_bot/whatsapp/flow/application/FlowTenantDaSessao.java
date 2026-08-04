package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

/**
 * Autoridade única sobre "de qual negócio é esta troca do Flow".
 *
 * O tenant vem SEMPRE da sessão — o payload do cliente não tem, e nunca terá, campo de
 * negócio. Concentrar a resposta aqui evita que cada handler invente a sua (e que algum
 * deles acabe aceitando um businessId vindo de fora).
 *
 * A sessão de preview do editor da Meta não tem tenant por construção; para ela vale o
 * negócio corrente, e apenas para EXIBIR catálogo e agenda. Preview segue incapaz de
 * agendar: essa divergência vive no handler de confirmação, antes de qualquer escrita.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowTenantDaSessao {

    private final TenantProvider tenantProvider;

    public FlowTenantDaSessao(TenantProvider tenantProvider) {
        this.tenantProvider = tenantProvider;
    }

    public BusinessId de(FlowSession session) {
        if (session != null && session.businessId() != null) {
            return BusinessId.from(session.businessId());
        }
        return tenantProvider.currentBusinessId();
    }
}
