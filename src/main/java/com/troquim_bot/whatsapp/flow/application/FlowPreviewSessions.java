package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.WhatsAppFlowProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * Política de PREVIEW do editor da Meta (Flow Builder).
 *
 * Por que existe: o Flow Builder testa a navegação dinâmica chamando o Data Endpoint com
 * um {@code flow_token} FIXO, configurado à mão no painel — e não com um token de sessão
 * real (256 bits, gerado no envio e amarrado a um cliente). Sem tratamento, esse token
 * cai no 427 e o editor não consegue passar da primeira tela.
 *
 * O que NÃO é afrouxado: uma sessão de preview não tem cliente nem tenant e NUNCA cria
 * agendamento. {@code ConfirmarAgendamentoHandler} reconhece {@link FlowSession#preview()}
 * e simula o sucesso ANTES de qualquer escrita no domínio. As telas de navegação são
 * leitura pura (catálogo fixo e disponibilidade), então o editor exercita o Flow real sem
 * abrir brecha no agendamento — mesmo que alguém adivinhe o token, o pior que consegue é
 * ver a agenda pública e uma tela de sucesso simulada.
 *
 * Desligado por padrão (produção-seguro). Só liga quando as TRÊS condições valem juntas:
 * {@code TROQUIM_WHATSAPP_FLOW_DRAFT=true}, {@code preview-token} configurado e o
 * {@code flow_token} recebido idêntico a esse token (comparação em tempo constante,
 * coerente com a disciplina de segurança do resto do endpoint). O par draft+token é
 * avaliado em {@link WhatsAppFlowProperties#temPreview()}.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowPreviewSessions {

    private final WhatsAppFlowProperties properties;

    public FlowPreviewSessions(WhatsAppFlowProperties properties) {
        this.properties = properties;
    }

    /**
     * O token recebido é EXATAMENTE o token de preview configurado?
     *
     * Falso quando o preview está desligado (draft off ou sem token) ou quando o token é
     * nulo/vazio. A comparação é em tempo constante para não vazar o token por timing.
     */
    public boolean ehTokenDePreview(String flowToken) {
        if (!properties.temPreview() || flowToken == null || flowToken.isBlank()) {
            return false;
        }
        byte[] esperado = properties.getPreviewToken().getBytes(StandardCharsets.UTF_8);
        byte[] recebido = flowToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recebido);
    }

    /**
     * Constrói a sessão efêmera de preview, válida pelo mesmo TTL de uma sessão comum
     * (tempo de sobra para o editor concluir o teste). Nunca é persistida.
     */
    public FlowSession novaSessao(String flowToken, LocalDateTime agora) {
        return FlowSession.preview(flowToken, agora,
                agora.plusMinutes(properties.getSessaoTtlMinutos()));
    }
}
