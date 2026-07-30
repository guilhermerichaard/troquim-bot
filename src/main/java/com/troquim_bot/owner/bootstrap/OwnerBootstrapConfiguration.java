package com.troquim_bot.owner.bootstrap;

import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.owner.application.OwnerUserRepository;
import com.troquim_bot.owner.application.PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fiação do bootstrap do primeiro owner: registra a config tipada
 * ({@link OwnerBootstrapProperties}) e declara o {@link OwnerBootstrapRunner} como bean.
 *
 * O runner é sempre criado, mas desligado por padrão ({@code enabled=false}) ele é um
 * no-op no startup — subir a aplicação sem as variáveis de bootstrap continua normal.
 */
@Configuration
@EnableConfigurationProperties(OwnerBootstrapProperties.class)
public class OwnerBootstrapConfiguration {

    @Bean
    OwnerBootstrapRunner ownerBootstrapRunner(OwnerBootstrapProperties properties,
                                              TenantProvider tenantProvider,
                                              OwnerUserRepository ownerUserRepository,
                                              PasswordHasher passwordHasher) {
        return new OwnerBootstrapRunner(properties, tenantProvider, ownerUserRepository, passwordHasher);
    }
}
