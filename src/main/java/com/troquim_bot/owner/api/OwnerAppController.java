package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.owner.application.OwnerDashboardService;
import com.troquim_bot.whatsapp.channel.infrastructure.meta.MetaEmbeddedSignupProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página /app: nome do negócio, agenda mínima e status do WhatsApp.
 *
 * O controller só extrai a identidade (já resolvida pelo {@link OwnerSessionCookieFilter}
 * na sessão) e delega a {@link OwnerDashboardService}. Nenhuma regra de agenda, tenant
 * ou canal mora aqui — só formatação de resposta.
 *
 * Chegar aqui sem sessão válida é impossível pela rota normal: {@code /app/**} exige
 * ROLE_OWNER em SecurityConfigDefaultDeny, então o Spring Security já barra com 403
 * antes do método rodar. A checagem abaixo é defensiva, não a linha de defesa real.
 */
@RestController
public class OwnerAppController {

    private final OwnerDashboardService dashboardService;
    private final OwnerAppPageRenderer renderer;
    private final MetaEmbeddedSignupProperties signupProperties;

    public OwnerAppController(OwnerDashboardService dashboardService, OwnerAppPageRenderer renderer,
                              MetaEmbeddedSignupProperties signupProperties) {
        this.dashboardService = dashboardService;
        this.renderer = renderer;
        this.signupProperties = signupProperties;
    }

    @GetMapping("/app")
    public ResponseEntity<String> app(HttpServletRequest request) {
        AuthenticatedOwner identidade = OwnerSessionCookieFilter.identidadeDe(request).orElse(null);
        if (identidade == null) {
            return ResponseEntity.status(403).build();
        }
        var dashboard = dashboardService.montar(identidade);
        String html = renderer.render(dashboard, signupProperties.getAppId(),
                signupProperties.getConfigId(), signupProperties.isEnabled());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + "; charset=UTF-8")
                .body(html);
    }
}
