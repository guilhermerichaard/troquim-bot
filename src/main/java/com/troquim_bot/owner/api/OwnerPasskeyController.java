package com.troquim_bot.owner.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/security/passkeys")
public class OwnerPasskeyController {

    private final PublicKeyCredentialUserEntityRepository users;
    private final UserCredentialRepository credentials;

    public OwnerPasskeyController(PublicKeyCredentialUserEntityRepository users,
                                  UserCredentialRepository credentials) {
        this.users = users;
        this.credentials = credentials;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        var owner = OwnerSessionCookieFilter.identidadeDe(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();

        var entity = users.findByUsername(owner.get().ownerId().toString());
        if (entity == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(credentials.findByUserId(entity.getId()).stream()
                .map(OwnerPasskeyController::view)
                .toList());
    }

    @DeleteMapping("/{credentialId}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable String credentialId) {
        var owner = OwnerSessionCookieFilter.identidadeDe(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();

        Bytes id;
        try {
            id = Bytes.fromBase64(credentialId);
        } catch (RuntimeException invalid) {
            return ResponseEntity.badRequest().build();
        }

        CredentialRecord record = credentials.findByCredentialId(id);
        if (record == null) return ResponseEntity.noContent().build();

        var entity = users.findByUsername(owner.get().ownerId().toString());
        if (entity == null || !record.getUserEntityUserId().equals(entity.getId())) {
            return ResponseEntity.status(404).build();
        }

        credentials.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Map<String,Object> view(CredentialRecord record) {
        return Map.of(
                "id", record.getCredentialId().toBase64UrlString(),
                "label", record.getLabel() == null ? "Passkey" : record.getLabel(),
                "createdAt", record.getCreated() == null ? "" : record.getCreated().toString(),
                "lastUsedAt", record.getLastUsed() == null ? "" : record.getLastUsed().toString()
        );
    }
}
