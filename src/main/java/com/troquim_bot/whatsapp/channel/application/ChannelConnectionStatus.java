package com.troquim_bot.whatsapp.channel.application;

/**
 * Estado do vínculo entre um tenant e sua conta WhatsApp Business.
 *
 * Só existem três, de propósito: o que o operador precisa saber é se pode mandar
 * mensagem por aquele canal, se ainda está esperando o Embedded Signup terminar, ou
 * se falhou e precisa recomeçar. Detalhe de diagnóstico não vira estado.
 */
public enum ChannelConnectionStatus {

    /** Início registrado; aguardando a finalização com o code da Meta. */
    PENDENTE,

    /** Credencial obtida e cifrada: o canal pode ser usado. */
    CONECTADO,

    /** A finalização falhou. Recomeçar gera um novo nonce sobre a mesma linha. */
    FALHOU
}
