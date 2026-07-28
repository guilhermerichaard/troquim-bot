package com.troquim_bot.whatsapp.channel.infrastructure;

import com.troquim_bot.whatsapp.channel.infrastructure.crypto.ChannelCryptoProperties;
import com.troquim_bot.whatsapp.channel.infrastructure.meta.MetaEmbeddedSignupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Registra a configuração tipada do onboarding de canal WhatsApp.
 *
 * Sem validação fail-fast no boot de propósito: o onboarding é opcional e vem
 * desligado, então uma instância sem ele configurado sobe normalmente — os endpoints
 * respondem 503 e a cifragem só é exigida quando alguém de fato tenta conectar.
 */
@Configuration
@EnableConfigurationProperties({MetaEmbeddedSignupProperties.class, ChannelCryptoProperties.class})
public class ChannelConfiguration {

    /**
     * Cliente dedicado da troca de OAuth code, com timeouts EXPLÍCITOS: a Graph API é
     * uma dependência externa no meio de uma transação, e uma chamada sem prazo
     * seguraria a conexão do banco até o sistema operacional desistir.
     *
     * Bean próprio (e não o {@code RestClient.Builder} auto-configurado, que este
     * projeto não expõe) para seguir o mesmo padrão do gateway da Cloud API.
     */
    @Bean
    RestClient metaOAuthRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
