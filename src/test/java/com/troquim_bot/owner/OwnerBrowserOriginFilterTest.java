package com.troquim_bot.owner;

import com.troquim_bot.owner.api.OwnerBrowserOriginFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OwnerBrowserOriginFilterTest {
    private int execute(String method, String path, String origin, String site) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (origin != null) request.addHeader("Origin", origin);
        if (site != null) request.addHeader("Sec-Fetch-Site", site);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new OwnerBrowserOriginFilter(Set.of("https://app.troquim.app"))
                .doFilter(request, response, chain);
        return response.getStatus();
    }
    @Test void crossOriginCannotLoginOrRevokeSessions() throws Exception {
        for (String origin : new String[]{"https://evil.example", "https://other.troquim.app", "null"}) {
            assertEquals(403, execute("POST", "/api/v1/owner/login", origin, "cross-site"));
            assertEquals(403, execute("DELETE", "/api/v1/app/security/sessions/example", origin, "same-site"));
        }
    }
    @Test void configuredOriginAndServerBffAreAccepted() throws Exception {
        assertEquals(200, execute("POST", "/api/v1/owner/login", "https://app.troquim.app", "same-site"));
        assertEquals(200, execute("POST", "/api/v1/app/appointments", null, null));
    }
    @Test void browserRequestWithoutOriginIsRejected() throws Exception {
        assertEquals(403, execute("POST", "/api/v1/owner/login", null, "same-origin"));
    }
    @Test void doesNotInterfereWithWebhooksOrReads() throws Exception {
        assertEquals(200, execute("POST", "/webhook/whatsapp/cloud", null, null));
        assertEquals(200, execute("GET", "/api/v1/app/appointments", null, "same-origin"));
    }
}
