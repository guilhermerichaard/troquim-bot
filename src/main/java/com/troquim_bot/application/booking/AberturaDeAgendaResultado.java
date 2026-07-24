package com.troquim_bot.application.booking;

/**
 * Desfecho da tentativa de abrir a agenda por WhatsApp Flow.
 *
 * O chamador (Conversation) precisa saber apenas uma coisa: assumiu-se o atendimento ou
 * ele deve seguir pelo caminho textual. Por isso {@link #abriu()} — e não um enum que
 * obrigasse cada chamador a enumerar motivos de indisponibilidade.
 */
public record AberturaDeAgendaResultado(Status status, String motivo) {

    public enum Status {
        /** Mensagem enviada; o cliente vai tocar no botão. */
        ENVIADO,
        /** Recurso desligado ou sem Flow configurado — nunca foi para produção. */
        INDISPONIVEL,
        /** O canal atual não sabe enviar Flow (ex.: Evolution API). */
        CANAL_NAO_SUPORTA,
        /** Configurado, mas o envio falhou. A sessão criada foi invalidada. */
        FALHA_NO_ENVIO
    }

    public boolean abriu() {
        return status == Status.ENVIADO;
    }

    public static AberturaDeAgendaResultado enviado() {
        return new AberturaDeAgendaResultado(Status.ENVIADO, null);
    }

    public static AberturaDeAgendaResultado indisponivel(String motivo) {
        return new AberturaDeAgendaResultado(Status.INDISPONIVEL, motivo);
    }

    public static AberturaDeAgendaResultado canalNaoSuporta() {
        return new AberturaDeAgendaResultado(Status.CANAL_NAO_SUPORTA, "canal sem suporte a Flow");
    }

    public static AberturaDeAgendaResultado falhaNoEnvio(String motivo) {
        return new AberturaDeAgendaResultado(Status.FALHA_NO_ENVIO, motivo);
    }
}
