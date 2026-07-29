package com.troquim_bot.owner.infrastructure;

import com.troquim_bot.owner.application.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt via Spring Security — já é dependência do projeto (spring-boot-starter-security). */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String senhaClara) {
        return encoder.encode(senhaClara);
    }

    @Override
    public boolean confere(String senhaClara, String hash) {
        return senhaClara != null && hash != null && encoder.matches(senhaClara, hash);
    }
}
