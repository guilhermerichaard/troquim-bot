package com.troquim_bot.controller.dto;

import com.troquim_bot.application.intent.IntentType;

public class DevConversationResponse {

    private String reply;
    private IntentType intent;
    private String conversationState;
    private String customer;
    private DebugInfo debug;

    public DevConversationResponse() {
    }

    public DevConversationResponse(String reply, IntentType intent, String conversationState,
                                   String customer, DebugInfo debug) {
        this.reply = reply;
        this.intent = intent;
        this.conversationState = conversationState;
        this.customer = customer;
        this.debug = debug;
    }

    public static DevConversationResponse of(String reply, IntentType intent, String conversationState,
                                             String customer, long processingTimeMs) {
        return new DevConversationResponse(reply, intent, conversationState, customer,
            new DebugInfo(processingTimeMs));
    }

    public String getReply() {
        return reply;
    }

    public IntentType getIntent() {
        return intent;
    }

    public String getConversationState() {
        return conversationState;
    }

    public String getCustomer() {
        return customer;
    }

    public DebugInfo getDebug() {
        return debug;
    }

    public static class DebugInfo {
        private long processingTimeMs;

        public DebugInfo() {
        }

        public DebugInfo(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }
    }
}