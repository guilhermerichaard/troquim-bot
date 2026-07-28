package com.troquim_bot.whatsapp.channel.api;

import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService.ConexaoInvalidaException;
import com.troquim_bot.whatsapp.channel.infrastructure.meta.MetaEmbeddedSignupProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Endpoints administrativos do onboarding "Conectar WhatsApp Business".
 *
 * Fronteira fina de propósito: valida a FORMA do que chega e delega. Nenhuma regra de
 * negócio mora aqui — quem decide validade de nonce, o que cifrar e quando marcar
 * conectado é o {@link ConectarWhatsAppChannelService}.
 *
 * O que sai daqui é sempre público: {@code appId}, {@code configId}, {@code state} e
 * status. App Secret e access token não têm representação nestes DTOs — não é uma
 * omissão a manter por disciplina, é impossível por construção.
 *
 * Protegido por {@code ROLE_ADMIN} (ver SecurityConfigDefaultDeny): o Embedded Signup
 * vincula uma conta ao tenant, então não pode ser iniciado anonimamente.
 */
@RestController
@RequestMapping("/api/v1/admin/whatsapp/connections")
public class WhatsAppChannelConnectionController {

    private final ConectarWhatsAppChannelService service;
    private final MetaEmbeddedSignupProperties properties;

    public WhatsAppChannelConnectionController(ConectarWhatsAppChannelService service,
                                               MetaEmbeddedSignupProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * Início: devolve o que o navegador precisa para abrir o diálogo da Meta.
     *
     * O {@code state} volta para o frontend porque é ele que a Meta devolverá junto
     * com o code — é o que amarra a volta ao início que aconteceu neste tenant.
     */
    @PostMapping("/start")
    public ResponseEntity<IniciarResponse> iniciar() {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        var resultado = service.iniciar();
        return ResponseEntity.ok(new IniciarResponse(
                resultado.state(),
                properties.getAppId(),
                properties.getConfigId(),
                resultado.status().name()));
    }

    /**
     * Finalização com o que voltou da Meta. Só {@code state} e {@code code} são
     * obrigatórios; WABA e phone number id são aceitos quando o Embedded Signup os
     * fornece na sessão.
     */
    @PostMapping("/finish")
    public ResponseEntity<FinalizarResponse> finalizar(@RequestBody(required = false) FinalizarRequest request) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (request == null || vazio(request.state()) || vazio(request.code())) {
            return ResponseEntity.badRequest().build();
        }
        var resultado = service.finalizar(
                request.state().trim(),
                request.code().trim(),
                normalizar(request.wabaId()),
                normalizar(request.phoneNumberId()));

        return ResponseEntity.ok(new FinalizarResponse(
                resultado.status().name(),
                resultado.wabaId().orElse(null),
                resultado.phoneNumberId().orElse(null)));
    }

    /** Status corrente do canal do tenant. Nunca inclui credencial. */
    @GetMapping("/current")
    public ResponseEntity<StatusResponse> atual() {
        Optional<ChannelConnection> conexao = service.consultar();
        if (conexao.isEmpty()) {
            return ResponseEntity.ok(new StatusResponse(null, null, null, false));
        }
        ChannelConnection c = conexao.get();
        return ResponseEntity.ok(new StatusResponse(
                c.status().name(),
                c.wabaId().orElse(null),
                c.phoneNumberId().orElse(null),
                c.status() == ChannelConnectionStatus.CONECTADO));
    }

    /**
     * Entrada recusada vira 400 seco. Sem corpo e sem motivo: distinguir "nonce
     * desconhecido" de "code recusado" ajudaria quem estivesse sondando.
     */
    @ExceptionHandler(ConexaoInvalidaException.class)
    public ResponseEntity<Void> conexaoInvalida() {
        return ResponseEntity.badRequest().build();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String normalizar(String valor) {
        return vazio(valor) ? null : valor.trim();
    }

    /** Só dados públicos: nenhum destes campos é segredo. */
    public record IniciarResponse(String state, String appId, String configId, String status) {}

    public record FinalizarRequest(String state, String code, String wabaId, String phoneNumberId) {}

    public record FinalizarResponse(String status, String wabaId, String phoneNumberId) {}

    public record StatusResponse(String status, String wabaId, String phoneNumberId, boolean conectado) {}
}
