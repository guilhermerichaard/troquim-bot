package com.troquim_bot.application.conversation.engine;

import java.util.Optional;

public record ExtractedEntities(
        String service,
        String day,
        String time,
        String customerName
) {
    public static ExtractedEntities empty() {
        return new ExtractedEntities(null, null, null, null);
    }

    public Optional<String> serviceOptional() {
        return Optional.ofNullable(service).filter(value -> !value.isBlank());
    }

    public Optional<String> dayOptional() {
        return Optional.ofNullable(day).filter(value -> !value.isBlank());
    }

    public Optional<String> timeOptional() {
        return Optional.ofNullable(time).filter(value -> !value.isBlank());
    }

    public Optional<String> customerNameOptional() {
        return Optional.ofNullable(customerName).filter(value -> !value.isBlank());
    }

    public boolean hasBookingData() {
        return serviceOptional().isPresent() || dayOptional().isPresent() || timeOptional().isPresent();
    }
}
