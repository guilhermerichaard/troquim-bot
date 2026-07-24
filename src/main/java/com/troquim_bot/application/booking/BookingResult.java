package com.troquim_bot.application.booking;

/**
 * Resultado da tentativa de confirmar um agendamento.
 *
 * Carrega apenas dados; a decisão de como responder ao cliente é de quem consome
 * (camada de conversa ou handler do Flow), que traduz o status em uma mensagem.
 *
 * A separação entre {@link Status#INDISPONIVEL} e {@link Status#FALHA_TECNICA} é
 * deliberada: mandar o cliente "escolher outro horário" quando a escrita falhou afirma
 * um diagnóstico que o sistema não tem. Conflito é um fato observado (existe agendamento
 * sobreposto); falha técnica é justamente a ausência de informação confiável.
 */
public record BookingResult(Status status, String mensagem,
                            String servico, String dia, String horario, String nome) {

    /**
     * Texto canônico de {@link Status#FALHA_TECNICA}, usado por TODOS os consumidores
     * (WhatsApp Flow e conversa textual) e também no caminho em que a confirmação lança e
     * a transação é desfeita.
     *
     * Deliberadamente NEUTRO. Numa falha técnica não sabemos se a escrita chegou a
     * ocorrer, se o rollback se aplicou, nem se o horário segue livre — o que falhou foi
     * exatamente o mecanismo que nos daria essa resposta. Por isso a mensagem não afirma
     * que o agendamento não foi criado, que o horário continua disponível, nem que
     * repetir dará no mesmo. Ela diz só o que é verificável: não concluímos, e o cliente
     * pode tentar de novo — com a repetição protegida pela idempotência.
     */
    public static final String MENSAGEM_FALHA_TECNICA =
            "Não conseguimos concluir seu agendamento agora. Tente novamente em instantes.";

    public enum Status {
        /** Customer, Reservation e Appointment criados/persistidos com sucesso. */
        CONFIRMADO,
        /**
         * Horário ocupado — conflito real, observado contra agendamentos ativos.
         * Nenhum dado parcial permanece e oferecer outro horário é a ação correta.
         */
        INDISPONIVEL,
        /** Dados do rascunho não puderam ser interpretados (dia/horário inválido). */
        INVALIDO,
        /**
         * A agenda permitia, mas a escrita falhou (banco, transação, infraestrutura).
         *
         * O estado resultante é INDETERMINADO do ponto de vista do caso de uso: a
         * compensação e o rollback são tentados, mas quem falhou foi o próprio mecanismo
         * de persistência, então não há evidência para afirmar o que ficou gravado. Não
         * derive daqui que o horário está livre nem que nada foi criado.
         */
        FALHA_TECNICA,
        /**
         * A base do comando (o {@code flow_token}) JÁ concluiu um agendamento, e esta
         * confirmação traz dados diferentes.
         *
         * Regra do MVP: um Flow aberto vale por um agendamento. Não é conflito de agenda
         * (o horário pedido pode estar livre) nem falha técnica (nada quebrou) — é a
         * sessão que se esgotou. O caminho correto para o cliente é abrir a agenda de novo.
         */
        SESSAO_JA_CONFIRMADA
    }

    public boolean isConfirmado() {
        return status == Status.CONFIRMADO;
    }

    /** Conflito real de agenda — oferecer outro horário é correto. */
    public boolean isConflito() {
        return status == Status.INDISPONIVEL;
    }

    /** Falha de infraestrutura — não afirme nada sobre a agenda a partir disto. */
    public boolean isFalhaTecnica() {
        return status == Status.FALHA_TECNICA;
    }

    /** A base do comando já produziu um agendamento; é preciso abrir a agenda de novo. */
    public boolean isSessaoJaConfirmada() {
        return status == Status.SESSAO_JA_CONFIRMADA;
    }

    public static BookingResult confirmado(String servico, String dia, String horario, String nome) {
        return new BookingResult(Status.CONFIRMADO, null, servico, dia, horario, nome);
    }

    public static BookingResult indisponivel(String mensagem) {
        return new BookingResult(Status.INDISPONIVEL, mensagem, null, null, null, null);
    }

    public static BookingResult invalido(String mensagem) {
        return new BookingResult(Status.INVALIDO, mensagem, null, null, null, null);
    }

    /**
     * Texto canônico da sessão esgotada. Distinto do de conflito (não fala em horário
     * ocupado) e do de falha técnica (não pede para tentar de novo, porque repetir NESTE
     * Flow nunca vai funcionar).
     */
    public static final String MENSAGEM_SESSAO_JA_CONFIRMADA =
            "Este agendamento já foi concluído. Para marcar outro horário, "
                    + "peça a agenda novamente na conversa.";

    public static BookingResult sessaoJaConfirmada() {
        return new BookingResult(Status.SESSAO_JA_CONFIRMADA, MENSAGEM_SESSAO_JA_CONFIRMADA,
                null, null, null, null);
    }

    /** Falha técnica com o texto canônico e neutro. */
    public static BookingResult falhaTecnica() {
        return new BookingResult(Status.FALHA_TECNICA, MENSAGEM_FALHA_TECNICA,
                null, null, null, null);
    }
}
