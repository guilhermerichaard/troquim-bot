package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerDashboardService.AgendaItem;
import com.troquim_bot.owner.application.OwnerDashboardService.OwnerDashboard;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Renderiza a página /app como HTML puro. Camada de apresentação: formata o que o
 * {@code OwnerDashboard} já decidiu, não decide nada — nenhuma regra de agenda, status
 * ou negócio mora aqui.
 *
 * Servidor puro (sem template engine no projeto): a única interatividade é o botão
 * "Conectar WhatsApp Business", que só carrega o SDK da Meta quando clicado — nada de
 * Meta roda na carga da página.
 */
@Component
public class OwnerAppPageRenderer {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public String render(OwnerDashboard dashboard, String appId, String configId, boolean signupHabilitado) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>TroQuim — ").append(esc(dashboard.nomeNegocio())).append("</title>")
            .append("<style>")
            .append("body{font-family:system-ui,sans-serif;max-width:480px;margin:2rem auto;padding:0 1rem;color:#1a1a1a}")
            .append("h1{font-size:1.25rem}h2{font-size:1rem;color:#555;margin-top:2rem}")
            .append(".item{border-bottom:1px solid #eee;padding:.6rem 0}")
            .append(".status{display:inline-block;padding:.2rem .6rem;border-radius:999px;font-size:.85rem}")
            .append(".s-conectado{background:#e6f4ea;color:#1e7e34}")
            .append(".s-pendente{background:#fff8e1;color:#8a6d00}")
            .append(".s-falhou{background:#fdecea;color:#a12622}")
            .append(".s-naoconectado{background:#eee;color:#555}")
            .append("button{background:#1a1a1a;color:#fff;border:0;border-radius:6px;padding:.7rem 1.2rem;font-size:1rem}")
            .append("button:disabled{opacity:.5}")
            .append("</style></head><body>")
            .append("<h1>").append(esc(dashboard.nomeNegocio())).append("</h1>")
            .append("<h2>Agenda</h2>")
            .append(renderAgenda(dashboard))
            .append("<h2>WhatsApp</h2>")
            .append(renderCanal(dashboard.statusCanal().orElse(null), appId, configId, signupHabilitado))
            .append("</body></html>");
        return html.toString();
    }

    private String renderAgenda(OwnerDashboard dashboard) {
        if (dashboard.agenda().isEmpty()) {
            return "<p>Nenhum agendamento próximo.</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (AgendaItem item : dashboard.agenda()) {
            sb.append("<div class=\"item\">")
              .append(item.data().format(DATA)).append(" ")
              .append(item.inicio().format(HORA)).append("–").append(item.fim().format(HORA))
              .append(" — ").append(esc(item.nomeCliente()))
              .append(" <small>(").append(esc(item.status())).append(")</small>")
              .append("</div>");
        }
        return sb.toString();
    }

    private String renderCanal(ChannelConnectionStatus status, String appId, String configId,
                               boolean signupHabilitado) {
        String rotulo = switch (status == null ? "" : status.name()) {
            case "CONECTADO" -> "conectado";
            case "PENDENTE" -> "conexão pendente";
            case "FALHOU" -> "falhou";
            default -> "não conectado";
        };
        String classe = switch (status == null ? "" : status.name()) {
            case "CONECTADO" -> "s-conectado";
            case "PENDENTE" -> "s-pendente";
            case "FALHOU" -> "s-falhou";
            default -> "s-naoconectado";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<p><span class=\"status ").append(classe).append("\">")
          .append(rotulo).append("</span></p>");

        boolean jaConectado = status == ChannelConnectionStatus.CONECTADO;
        sb.append("<button id=\"btn-conectar\" ")
          .append(!signupHabilitado || jaConectado ? "disabled" : "")
          .append(" onclick=\"troquimConectarWhatsApp()\">Conectar WhatsApp Business</button>");

        if (signupHabilitado) {
            sb.append(scriptEmbeddedSignup(appId, configId));
        }
        return sb.toString();
    }

    /**
     * O appId e o configId são PÚBLICOS — é exatamente o que este script recebe e usa.
     * App Secret, access token e qualquer segredo NÃO têm representação em lugar
     * nenhum deste HTML: o backend nunca os devolve a este endpoint.
     */
    private String scriptEmbeddedSignup(String appId, String configId) {
        return "<script>"
            + "let troquimFbCarregado=false;"
            + "function troquimCarregarFbSdk(cb){"
            + "  if(troquimFbCarregado){cb();return;}"
            + "  window.fbAsyncInit=function(){"
            + "    FB.init({appId:'" + jsEsc(appId) + "',autoLogAppEvents:true,xfbml:false,version:'v21.0'});"
            + "    troquimFbCarregado=true; cb();"
            + "  };"
            + "  var s=document.createElement('script');"
            + "  s.src='https://connect.facebook.net/pt_BR/sdk.js'; s.async=true;"
            + "  document.body.appendChild(s);"
            + "}"
            + "function troquimConectarWhatsApp(){"
            + "  var btn=document.getElementById('btn-conectar'); btn.disabled=true;"
            + "  fetch('/api/v1/app/whatsapp/connection/start',{method:'POST'})"
            + "    .then(function(r){return r.json();})"
            + "    .then(function(inicio){"
            + "      troquimCarregarFbSdk(function(){"
            + "        FB.login(function(resposta){"
            + "          if(!resposta.authResponse||!resposta.authResponse.code){btn.disabled=false;return;}"
            + "          fetch('/api/v1/app/whatsapp/connection/finish',{"
            + "            method:'POST',headers:{'Content-Type':'application/json'},"
            + "            body:JSON.stringify({state:inicio.state,code:resposta.authResponse.code})"
            + "          }).then(function(){window.location.reload();});"
            + "        },{config_id:'" + jsEsc(configId) + "',response_type:'code',override_default_response_type:true});"
            + "      });"
            + "    });"
            + "}"
            + "</script>";
    }

    private static String esc(String v) {
        return v == null ? "" : v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String jsEsc(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("'", "\'");
    }
}
