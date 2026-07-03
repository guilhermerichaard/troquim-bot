package com.troquim_bot.application.intent;

public record IntentResult(IntentType type) {

    public IntentResult {
        if (type == null) {
            throw new IllegalArgumentException("IntentType e obrigatorio");
        }
    }
}
