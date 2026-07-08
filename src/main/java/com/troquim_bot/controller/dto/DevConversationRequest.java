package com.troquim_bot.controller.dto;

public class DevConversationRequest {

    private String number;

    private String message;

    public DevConversationRequest() {
    }

    public DevConversationRequest(String number, String message) {
        this.number = number;
        this.message = message;
    }

    public String getNumber() {
        return number;
    }

    public String getMessage() {
        return message;
    }
}