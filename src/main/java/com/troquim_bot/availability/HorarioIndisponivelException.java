package com.troquim_bot.availability;

/**
 * O horário pedido está ocupado — regra de NEGÓCIO, não falha técnica.
 *
 * Existe para que os casos de uso possam separar "não dá para agendar nesse horário"
 * de "a escrita falhou", sem inspecionar mensagem de exceção. Antes desta classe, o
 * conflito e uma falha de banco chegavam ao chamador como a mesma
 * {@link IllegalArgumentException}, e o cliente recebia "escolha outro horário" quando
 * o problema era infraestrutura — mandando-o tentar horários que também falhariam.
 *
 * Estende {@link IllegalArgumentException} de propósito: os chamadores e testes que já
 * capturavam esse tipo continuam funcionando; quem precisa da distinção captura o tipo
 * específico primeiro.
 */
public class HorarioIndisponivelException extends IllegalArgumentException {

    public HorarioIndisponivelException(String message) {
        super(message);
    }
}
