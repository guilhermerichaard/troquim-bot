package com.troquim_bot.whatsapp.channel.infrastructure.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.troquim_bot.whatsapp.channel.application.MetaOAuthGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Troca do OAuth code por access token na Graph API.
 *
 * É o único ponto do sistema que usa o App Secret. O code chega da Application e o
 * token volta em claro apenas como valor de retorno — não é logado, não é guardado em
 * campo e o chamador o cifra imediatamente.
 *
 * Nenhum corpo de erro da Meta é propagado: respostas de OAuth costumam ecoar os
 * parâmetros enviados (inclusive o code), então só o fato da recusa sobe.
 */
@Component
public class GraphApiMetaOAuthGateway implements MetaOAuthGateway {

    private static final Logger log = LoggerFactory.getLogger(GraphApiMetaOAuthGateway.class);

    private final RestClient restClient;
    private final MetaEmbeddedSignupProperties properties;

    public GraphApiMetaOAuthGateway(@Qualifier("metaOAuthRestClient") RestClient restClient,
                                    MetaEmbeddedSignupProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String trocarCodePorToken(String code) {
        if (!properties.configurado()) {
            // Falha explícita: sem configuração completa, nada de "conectar por engano".
            throw new MetaOAuthException("Embedded Signup nao esta configurado");
        }
        if (code == null || code.isBlank()) {
            throw new MetaOAuthException("code ausente");
        }

        String uri = properties.getBaseUrl() + "/" + properties.getGraphApiVersion()
                + "/oauth/access_token";

        JsonNode resposta;
        try {
            resposta = restClient.get()
                    .uri(uri, uriBuilder -> uriBuilder
                            .queryParam("client_id", properties.getAppId())
                            .queryParam("client_secret", properties.getAppSecret())
                            .queryParam("code", code)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Graph API recusou a troca de code: {}", e.getClass().getSimpleName());
            throw new MetaOAuthException("troca de code recusada");
        }

        if (resposta == null || !resposta.hasNonNull("access_token")
                || resposta.get("access_token").asText().isBlank()) {
            throw new MetaOAuthException("resposta da Meta sem access_token");
        }
        return resposta.get("access_token").asText();
    }
}
