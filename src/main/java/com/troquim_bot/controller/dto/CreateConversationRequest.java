package com.troquim_bot.controller.dto;

public class CreateConversationRequest {

    private String customerId;

    public CreateConversationRequest() {
    }

    public CreateConversationRequest(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
