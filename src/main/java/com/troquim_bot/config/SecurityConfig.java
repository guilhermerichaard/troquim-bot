package com.troquim_bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança para liberar H2 Console e permitir acesso aos endpoints da API.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.anyRequest().permitAll()
			)
			.headers(headers -> headers
				.frameOptions(frame -> frame.disable())
			)
			.csrf(csrf -> csrf.disable());

		return http.build();
	}
}
