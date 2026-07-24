package com.troquim_bot.application.booking;

/**
 * Capacidade de abrir a agenda do cliente numa experiência rica (hoje, WhatsApp Flow).
 *
 * Existe para que a camada de conversa possa oferecer a agenda sem conhecer WhatsApp
 * Flow, Meta ou criptografia: ela pergunta "essa capacidade existe?" e "abre para este
 * cliente", e nada mais. Toda a implementação vive no módulo do Flow, do outro lado
 * desta interface.
 *
 * Modelada como capacidade OPCIONAL: quando o recurso está desligado, não configurado ou
 * o canal não suporta, o bean simplesmente não existe. Assim o fallback textual é o
 * comportamento natural do sistema, e não uma cadeia de flags espalhada pela conversa.
 */
public interface AberturaDeAgenda {

    /** Vale a pena tentar? Falso quando desligado, não configurado ou sem canal. */
    boolean disponivel();

    /**
     * Abre a agenda para um cliente.
     *
     * @param telefone telefone E.164 de origem CONFIÁVEL (webhook autenticado), nunca
     *                 um valor informado pelo próprio cliente numa tela
     * @return desfecho; nunca lança por indisponibilidade — isso é fluxo normal
     */
    AberturaDeAgendaResultado abrirPara(String telefone);
}
