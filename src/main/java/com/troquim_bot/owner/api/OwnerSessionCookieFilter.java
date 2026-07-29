package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.owner.application.OwnerAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Resolve o cookie de sessão do dono e, se válido, autentica a requisição com
 * ROLE_OWNER e disponibiliza {@link AuthenticatedOwner} como atributo do request.
 *
 * Espelha {@code BearerTokenFilter} (mesmo ponto da cadeia, mesmo padrão de "ausente ou
 * inválido = segue sem autenticar", nunca 401 aqui — quem decide 401/403 é o
 * authorizeHttpRequests). businessId nunca é lido do request nem de header: só da sessão.
 */
public class OwnerSessionCookieFilter extends OncePerRequestFilter {

    static final String OWNER_ROLE = "ROLE_OWNER";
    static final String REQUEST_ATTR = "troquim.authenticatedOwner";

    private final OwnerAuthService ownerAuthService;

    public OwnerSessionCookieFilter(OwnerAuthService ownerAuthService) {
        this.ownerAuthService = ownerAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        tokenDoCookie(request).flatMap(ownerAuthService::resolver).ifPresent(owner -> {
            request.setAttribute(REQUEST_ATTR, owner);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    owner.ownerId().toString(), null, List.of(new SimpleGrantedAuthority(OWNER_ROLE))));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }

    private Optional<String> tokenDoCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (OwnerSessionCookie.NOME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    /** Extrai a identidade já resolvida pelo filtro. Único ponto que os controllers usam. */
    public static Optional<AuthenticatedOwner> identidadeDe(HttpServletRequest request) {
        Object attr = request.getAttribute(REQUEST_ATTR);
        return attr instanceof AuthenticatedOwner owner ? Optional.of(owner) : Optional.empty();
    }
}
