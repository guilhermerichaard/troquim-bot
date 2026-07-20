package com.troquim_bot.infrastructure.whatsappcloud;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fiação da integração WhatsApp Cloud. As propriedades tipadas são sempre
 * registradas (para ler {@code enabled=false} quando desligada); os demais beans
 * são condicionais à feature flag ({@link ConditionalOnWhatsAppCloud}).
 *
 * O {@link RestClient} outbound tem timeouts EXPLÍCITOS (connect/read) e é injetado
 * no gateway — permitindo, em teste, apontar para um servidor fake e exercitar
 * timeout/erros sem mocks do gateway.
 */
@Configuration
@EnableConfigurationProperties(WhatsAppCloudProperties.class)
public class WhatsAppCloudConfiguration {

    @Bean
    @ConditionalOnWhatsAppCloud
    RestClient whatsAppCloudRestClient(WhatsAppCloudProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
