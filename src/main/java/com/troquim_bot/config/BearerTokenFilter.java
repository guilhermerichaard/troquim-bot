package com.troquim_bot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Authenticates the MVP administrative Bearer token. */
public class BearerTokenFilter extends OncePerRequestFilter {

    static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final String adminApiKey;

    public BearerTokenFilter(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (isBearer(authHeader) && isConfigured()) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty() && secureCompare(token, adminApiKey)) {
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority(ADMIN_ROLE)))
                );
                SecurityContextHolder.setContext(securityContext);
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isConfigured() {
        return adminApiKey != null
            && !adminApiKey.isBlank()
            && !AdminApiKeyConfig.PLACEHOLDER.equals(adminApiKey);
    }

    private static boolean isBearer(String authHeader) {
        return authHeader != null
            && authHeader.length() >= 7
            && authHeader.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    static boolean secureCompare(String first, String second) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return MessageDigest.isEqual(
                digest.digest(first.getBytes(StandardCharsets.UTF_8)),
                digest.digest(second.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
