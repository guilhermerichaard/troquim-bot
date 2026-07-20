package com.troquim_bot.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Explicit public allowlist with a deny-by-default boundary. */
@Configuration
@EnableWebSecurity
public class SecurityConfigDefaultDeny {

    private final String adminApiKey;
    private final Environment environment;

    public SecurityConfigDefaultDeny(@Qualifier("adminApiKey") String adminApiKey,
                                     Environment environment) {
        this.adminApiKey = adminApiKey;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        BearerTokenFilter bearerTokenFilter = new BearerTokenFilter(adminApiKey);
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));

        http
            .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.POST,
                        "/webhook/whatsapp",
                        "/webhook/whatsapp/messages-upsert")
                    .permitAll();
                // WhatsApp Cloud API (Meta): GET (handshake) e POST (eventos) exatos.
                // O POST e' publico no Security, mas protegido pela assinatura HMAC da Meta.
                // Rotas vizinhas (/webhook/whatsapp/cloud/**) NAO sao liberadas.
                auth.requestMatchers(HttpMethod.GET, "/webhook/whatsapp/cloud")
                    .permitAll();
                auth.requestMatchers(HttpMethod.POST, "/webhook/whatsapp/cloud")
                    .permitAll();
                auth.requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll();
                if (devProfile) {
                    auth.requestMatchers("/dev/**").permitAll();
                }
                auth.requestMatchers(
                        "/appointments/**", "/availability/**", "/business/**",
                        "/clientes/**", "/conversations/**", "/customers/**",
                        "/ordens/**", "/professionals/**", "/reservations/**", "/services/**")
                    .hasRole("ADMIN");
                auth.anyRequest().denyAll();
            })
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)));

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
