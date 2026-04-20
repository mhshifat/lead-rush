package com.leadrush.config;

import com.leadrush.apikey.service.ApiKeyAuthenticationFilter;
import com.leadrush.security.JwtAuthenticationFilter;
import com.leadrush.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final LeadRushProperties properties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            // OAuth2 needs a short-lived session to carry the `state` param across
            // the round-trip to Google/GitHub. IF_REQUIRED lets Spring Security
            // create a session ONLY during the OAuth handshake; JWT-authenticated
            // requests still flow through without touching one.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/resend-activation",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    // OAuth2 authorize + callback endpoints — Spring Security
                    // serves these itself when we customise baseUri below.
                    "/api/v1/auth/oauth2/**",
                    "/api/v1/public/**",
                    "/api/v1/webhooks/**",
                    "/t/**",
                    "/unsub/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/ws/**",
                    "/ws-public/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            // OAuth2 login: only active when GOOGLE_CLIENT_ID / GITHUB_CLIENT_ID
            // env vars are set (Spring auto-skips registrations with blank creds).
            // Custom base URIs keep the endpoints under /api/v1/auth/* alongside
            // the rest of the auth surface instead of Spring's default paths.
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(ae -> ae.baseUri("/api/v1/auth/oauth2/authorize"))
                .redirectionEndpoint(re -> re.baseUri("/api/v1/auth/oauth2/callback/*"))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    // Common failure: user clicked "deny" on the provider consent page.
                    String base = properties.getFrontendUrl() == null
                            ? "http://localhost:4000"
                            : properties.getFrontendUrl().split(",")[0].trim();
                    response.sendRedirect(base + "/auth/login?error=oauth_denied");
                })
            )

            // API-key filter runs before JWT: valid X-API-Key bypasses JWT.
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
