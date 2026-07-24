package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registro da configuração tipada do WhatsApp Flow.
 *
 * As properties são habilitadas incondicionalmente (é só leitura de configuração); os
 * beans do módulo é que são condicionais à feature flag, via {@link ConditionalOnWhatsAppFlow}.
 * Assim a aplicação sobe sem chave privada enquanto o Flow estiver desligado.
 */
@Configuration
@EnableConfigurationProperties(WhatsAppFlowProperties.class)
public class WhatsAppFlowConfiguration {
}
