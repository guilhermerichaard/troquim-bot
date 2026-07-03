package com.troquim_bot.application.intent;

public interface IntentEngine {

    IntentResult classify(String message);
}
