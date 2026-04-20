package com.leadrush.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Triggered by Spring Security whenever an unauthenticated request hits a
 * protected endpoint. Default behavior (with {@code .oauth2Login()} enabled)
 * is a 302 redirect to Spring's generated login page — useless for an API
 * consumer and misleading (the client sees HTML where JSON was expected).
 *
 * We return a tidy 401 with the same envelope shape the rest of the API uses,
 * so the frontend's error pipeline ({@code useErrorHandler} + the ofetch
 * interceptor) can treat it uniformly.
 *
 * OAuth2 authorize + callback endpoints are unaffected — they live behind a
 * different filter chain (OAuth2AuthorizationRequestRedirectFilter and
 * AbstractAuthenticationProcessingFilter) that handle the redirect dance
 * themselves before reaching this entry point.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "category", "AUTH",
                        "message", "Authentication required",
                        "path", request.getRequestURI(),
                        "timestamp", Instant.now().toString()
                )
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
