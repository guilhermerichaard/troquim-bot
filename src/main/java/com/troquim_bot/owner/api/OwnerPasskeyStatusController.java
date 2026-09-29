package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerPasskeyProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OwnerPasskeyStatusController {
    private final OwnerPasskeyProperties properties;

    public OwnerPasskeyStatusController(OwnerPasskeyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/v1/owner/passkey/status")
    public Map<String,Boolean> status() {
        return Map.of("enabled", properties.configured());
    }
}
