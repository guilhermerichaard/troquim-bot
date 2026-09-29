package com.troquim_bot.owner.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** Browser CSRF boundary. Server-side BFF requests carry no browser Origin/Fetch Metadata. */
public final class OwnerBrowserOriginFilter extends OncePerRequestFilter {
    private final Set<String> origins;
    public OwnerBrowserOriginFilter(Set<String> origins) { this.origins = Set.copyOf(origins); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                || !(path.startsWith("/api/v1/owner/") || path.startsWith("/api/v1/app/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String site = request.getHeader("Sec-Fetch-Site");
        if ((origin != null && !origins.contains(origin)) || (origin == null && site != null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
