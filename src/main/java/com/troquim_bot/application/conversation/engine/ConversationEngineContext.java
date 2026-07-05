package com.troquim_bot.application.conversation.engine;

import com.troquim_bot.application.intent.IntentResult;

public class ConversationEngineContext {

    private final String numero;
    private final String mensagem;
    private IntentResult intentResult;
    private ExtractedEntities entities;
    private String resposta;
    private boolean finalizado;

    public ConversationEngineContext(String numero, String mensagem) {
        this.numero = numero;
        this.mensagem = mensagem;
        this.entities = ExtractedEntities.empty();
    }

    public String numero() {
        return numero;
    }

    public String mensagem() {
        return mensagem;
    }

    public IntentResult intentResult() {
        return intentResult;
    }

    public void definirIntentResult(IntentResult intentResult) {
        this.intentResult = intentResult;
    }

    public ExtractedEntities entities() {
        return entities;
    }

    public void definirEntities(ExtractedEntities entities) {
        this.entities = entities == null ? ExtractedEntities.empty() : entities;
    }

    public String resposta() {
        return resposta;
    }

    public void responder(String resposta) {
        this.resposta = resposta;
        this.finalizado = true;
    }

    public boolean finalizado() {
        return finalizado;
    }
}
