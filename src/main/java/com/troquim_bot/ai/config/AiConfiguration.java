package com.troquim_bot.ai.config;

import org.springframework.stereotype.Component;

@Component
public class AiConfiguration {

    private final String model = "llama3.1:8b";
    private final double temperature = 0.6;
    private final int maxTokens = 500;

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}