package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/app/security/sessions")
public class OwnerSecurityController {
    private final OwnerAuthService auth;
    public OwnerSecurityController(OwnerAuthService auth) { this.auth = auth; }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        var owner = OwnerSessionCookieFilter.identidadeDe(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(auth.listarSessoes(owner.get(), token(request)));
    }

    @PostMapping("/revoke-others")
    public ResponseEntity<Void> revokeOthers(HttpServletRequest request) {
        var owner = OwnerSessionCookieFilter.identidadeDe(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();
        auth.encerrarOutrasSessoes(owner.get(), token(request));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(HttpServletRequest request, @PathVariable String id) {
        var owner = OwnerSessionCookieFilter.identidadeDe(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();
        auth.encerrarSessao(owner.get(), id);
        return ResponseEntity.noContent().build();
    }

    private static String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (OwnerSessionCookie.NOME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
