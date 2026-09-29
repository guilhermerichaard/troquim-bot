package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/owner")
public class OwnerAuthController {

    private final OwnerAuthService ownerAuthService;

    public OwnerAuthController(OwnerAuthService ownerAuthService) {
        this.ownerAuthService = ownerAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody(required = false) LoginRequest request) {
        if (request == null || vazio(request.email()) || vazio(request.senha())) {
            return ResponseEntity.badRequest().build();
        }
        return ownerAuthService.autenticar(request.email(), request.senha())
                .map(token -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE,
                                OwnerSessionCookie.create(token, OwnerSessionCookie.MAX_AGE_SECONDS).toString())
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        for (Cookie c : orVazio(request.getCookies())) {
            if (OwnerSessionCookie.NOME.equals(c.getName())) {
                ownerAuthService.encerrar(c.getValue());
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, OwnerSessionCookie.create("", 0).toString())
                .build();
    }

    private static Cookie[] orVazio(Cookie[] cookies) {
        return cookies == null ? new Cookie[0] : cookies;
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }

    public record LoginRequest(String email, String senha) {}
}
