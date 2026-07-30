package com.troquim_bot.owner.bootstrap;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.owner.application.OwnerUserRepository;
import com.troquim_bot.owner.application.PasswordHasher;
import com.troquim_bot.owner.domain.OwnerUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Provisiona o PRIMEIRO owner do /app de forma idempotente — sem endpoint público de
 * cadastro e sem INSERT manual no banco.
 *
 * DESLIGADO por padrão ({@code troquim.owner.bootstrap.enabled=false}): só age quando
 * ligado explicitamente no deploy. Ligado, cria uma única vez o owner do e-mail
 * informado, vinculado ao negócio piloto — o MESMO business_id de
 * {@code troquim.tenant.pilot-business-id}, resolvido pelo {@link TenantProvider},
 * nunca um UUID literal aqui. Se o e-mail já existe, é no-op: reexecutar o startup
 * nunca duplica nem sobrescreve o dono.
 *
 * A senha em claro vem SÓ de variável de ambiente (TROQUIM_OWNER_BOOTSTRAP_PASSWORD),
 * é hasheada com BCrypt via {@link PasswordHasher} e nunca entra no domínio, no banco
 * em claro, nem em log. As mensagens de log são genéricas de propósito — não incluem
 * e-mail, senha nem hash.
 */
public class OwnerBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapRunner.class);

    private final OwnerBootstrapProperties properties;
    private final TenantProvider tenantProvider;
    private final OwnerUserRepository ownerUserRepository;
    private final PasswordHasher passwordHasher;

    public OwnerBootstrapRunner(OwnerBootstrapProperties properties,
                                TenantProvider tenantProvider,
                                OwnerUserRepository ownerUserRepository,
                                PasswordHasher passwordHasher) {
        this.properties = properties;
        this.tenantProvider = tenantProvider;
        this.ownerUserRepository = ownerUserRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return; // desligado: nenhuma ação
        }

        String email = properties.getEmail();
        String senhaClara = properties.getPassword();
        if (vazio(email) || vazio(senhaClara)) {
            // Bootstrap ligado com configuração incompleta é erro de operação: falha
            // explícita no startup em vez de subir sem criar o dono. A mensagem diz o
            // QUE falta, nunca o CONTEÚDO das variáveis.
            throw new IllegalStateException(
                    "troquim.owner.bootstrap.enabled=true exige TROQUIM_OWNER_BOOTSTRAP_EMAIL e "
                            + "TROQUIM_OWNER_BOOTSTRAP_PASSWORD preenchidas. Nenhum owner foi criado.");
        }

        String emailNormalizado = email.trim().toLowerCase();
        if (ownerUserRepository.existePorEmail(emailNormalizado)) {
            log.info("Bootstrap de owner: e-mail ja existe, nenhuma acao (idempotente).");
            return;
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        OwnerUser owner = OwnerUser.novo(businessId, emailNormalizado, passwordHasher.hash(senhaClara));
        ownerUserRepository.salvar(owner);
        log.info("Bootstrap de owner: primeiro owner criado e vinculado ao negocio piloto.");
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }
}
