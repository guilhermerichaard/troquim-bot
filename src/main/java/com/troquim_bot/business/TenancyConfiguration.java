package com.troquim_bot.business;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra a configuração tipada de tenancy ({@link TenantProperties}).
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class TenancyConfiguration {
}
