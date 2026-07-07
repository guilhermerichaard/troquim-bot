package com.troquim_bot.application.conversation;

import com.troquim_bot.application.intent.IntentType;

public interface ConversationMessageProcessor {

    String gerarResposta(String numero, String mensagem);

    default String gerarResposta(String numero, String mensagem, IntentType v2IntentType) {
        return gerarResposta(numero, mensagem);
    }
}