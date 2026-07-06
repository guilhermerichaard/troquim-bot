package com.troquim_bot.ai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiConfiguration {

    private final String model;
    private final double temperature;
    private final int maxTokens;

    // No-arg constructor with defaults for backward compatibility (tests)
    public AiConfiguration() {
        this.model = "llama3.1:8b";
        this.temperature = 0.6;
        this.maxTokens = 500;
    }

    @Autowired
    public AiConfiguration(@Value("${ai.model:llama3.1:8b}") String model,
                           @Value("${ai.temperature:0.6}") double temperature,
                           @Value("${ai.max-tokens:500}") int maxTokens) {
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

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