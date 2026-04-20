package com.leadrush.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Holds the PasswordEncoder bean in isolation so it has no transitive deps.
 *
 * Why it lives here instead of SecurityConfig:
 *   AuthService → PasswordEncoder (defined in SecurityConfig)
 *   SecurityConfig → OAuth2LoginSuccessHandler
 *   OAuth2LoginSuccessHandler → AuthService
 *       ↳ cycle. Spring refuses to wire it.
 *
 * Moving PasswordEncoder to a leaf config breaks the chain — AuthService now
 * pulls the encoder from a class that itself depends on nothing. SecurityConfig
 * keeps its FilterChain wiring plus the OAuth handler injection, and no bean
 * in the graph transitively depends on both AuthService and the filter chain.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
