package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração tipada do WhatsApp Flow de agendamento.
 * Prefixo {@code troquim.integrations.whatsapp.flow}.
 *
 * A chave privada é SEMPRE fornecida por variável de ambiente
 * ({@code WHATSAPP_FLOW_PRIVATE_KEY}) — nunca versionada, nunca registrada em log.
 * Aceita PEM PKCS#8 (com ou sem senha), com quebras de linha reais ou escapadas
 * como {@code \n} (formato usual em variáveis de ambiente).
 */
@ConfigurationProperties(prefix = "troquim.integrations.whatsapp.flow")
public class WhatsAppFlowProperties {

    /** Liga/desliga o endpoint do Flow (feature flag). Default: desligado. */
    private boolean enabled = false;

    /** Chave privada RSA em PEM PKCS#8. Sem default. */
    private String privateKey;

    /** Senha da chave privada, quando o PEM for {@code ENCRYPTED PRIVATE KEY}. */
    private String privateKeyPassword;

    /** Janela (em dias) oferecida no CalendarPicker a partir de hoje. */
    private int janelaDias = 30;

    /** Id do Flow publicado na Meta. Sem default: identificador de ambiente. */
    private String flowId;

    /** Nome do Flow — alternativa ao id, para ambientes onde só o nome é estável. */
    private String flowName;

    /** Rótulo do botão que abre a agenda. Limite da Meta: 20 caracteres. */
    private String cta = "Abrir agenda";

    /** Texto da mensagem que acompanha o botão. */
    private String mensagem = "Posso te mostrar os horários livres? É só tocar no botão.";

    /** {@code true} abre o Flow em modo draft — apenas para teste interno. */
    private boolean modoRascunho = false;

    /**
     * Validade da sessão, em minutos. Curta o bastante para limitar a janela de um token
     * vazado, longa o bastante para o cliente concluir sem pressa.
     */
    private int sessaoTtlMinutos = 30;

    /**
     * A integração está utilizável de fato? {@code enabled} liga os beans; isto verifica
     * se há Flow configurado para enviar. Sem isso, o caso de uso cai no texto.
     */
    public boolean temFlowConfigurado() {
        return (flowId != null && !flowId.isBlank()) || (flowName != null && !flowName.isBlank());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyPassword() {
        return privateKeyPassword;
    }

    public void setPrivateKeyPassword(String privateKeyPassword) {
        this.privateKeyPassword = privateKeyPassword;
    }

    public int getJanelaDias() {
        return janelaDias;
    }

    public void setJanelaDias(int janelaDias) {
        this.janelaDias = janelaDias;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public String getCta() {
        return cta;
    }

    public void setCta(String cta) {
        this.cta = cta;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public boolean isModoRascunho() {
        return modoRascunho;
    }

    public void setModoRascunho(boolean modoRascunho) {
        this.modoRascunho = modoRascunho;
    }

    public int getSessaoTtlMinutos() {
        return sessaoTtlMinutos;
    }

    public void setSessaoTtlMinutos(int sessaoTtlMinutos) {
        this.sessaoTtlMinutos = sessaoTtlMinutos;
    }
}
