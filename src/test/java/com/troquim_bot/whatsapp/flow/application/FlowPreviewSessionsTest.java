package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStatus;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.WhatsAppFlowProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portão de ativação do preview do editor da Meta.
 *
 * A regra é uma conjunção de TRÊS condições — draft ligado, token configurado e token
 * recebido idêntico. Este teste prende cada condição isoladamente, para que afrouxar
 * qualquer uma (em especial o draft) falhe aqui antes de chegar ao endpoint.
 */
@DisplayName("FlowPreviewSessions - portão de ativação")
class FlowPreviewSessionsTest {

    private static final String TOKEN = "teste-integridade-20260725";

    private static FlowPreviewSessions comPropriedades(boolean draft, String previewToken) {
        WhatsAppFlowProperties props = new WhatsAppFlowProperties();
        props.setModoRascunho(draft);
        props.setPreviewToken(previewToken);
        return new FlowPreviewSessions(props);
    }

    @Test
    @DisplayName("draft=true + token configurado + token idêntico → é preview")
    void ligadoQuandoAsTresCondicoesValem() {
        assertTrue(comPropriedades(true, TOKEN).ehTokenDePreview(TOKEN));
    }

    @Test
    @DisplayName("draft=false desliga o preview mesmo com o token idêntico")
    void draftDesligadoNuncaEhPreview() {
        assertFalse(comPropriedades(false, TOKEN).ehTokenDePreview(TOKEN),
                "Sem modo rascunho o token de preview não pode ser aceito");
    }

    @Test
    @DisplayName("draft=true mas sem token configurado → não é preview")
    void semTokenConfiguradoNaoEhPreview() {
        assertFalse(comPropriedades(true, null).ehTokenDePreview(TOKEN));
        assertFalse(comPropriedades(true, "   ").ehTokenDePreview(TOKEN));
    }

    @Test
    @DisplayName("token diferente do configurado → não é preview")
    void tokenDiferenteNaoEhPreview() {
        assertFalse(comPropriedades(true, TOKEN).ehTokenDePreview("outro-token"));
    }

    @Test
    @DisplayName("token recebido nulo ou vazio → não é preview")
    void tokenRecebidoNuloOuVazio() {
        FlowPreviewSessions preview = comPropriedades(true, TOKEN);
        assertFalse(preview.ehTokenDePreview(null));
        assertFalse(preview.ehTokenDePreview("  "));
    }

    @Test
    @DisplayName("a sessão de preview é efêmera: sem cliente, ABERTA e válida pelo TTL")
    void sessaoDePreviewEhEfemera() {
        WhatsAppFlowProperties props = new WhatsAppFlowProperties();
        props.setModoRascunho(true);
        props.setPreviewToken(TOKEN);
        props.setSessaoTtlMinutos(30);
        FlowPreviewSessions preview = new FlowPreviewSessions(props);

        LocalDateTime agora = LocalDateTime.now();
        FlowSession sessao = preview.novaSessao(TOKEN, agora);

        assertTrue(sessao.preview());
        assertNull(sessao.telefone(), "Preview não tem cliente real");
        assertNull(sessao.businessId(), "Preview não tem tenant real");
        assertEquals(FlowSessionStatus.ABERTA, sessao.status());
        assertTrue(sessao.utilizavel(agora));
        assertEquals(agora.plusMinutes(30), sessao.expiraEm());
    }
}
