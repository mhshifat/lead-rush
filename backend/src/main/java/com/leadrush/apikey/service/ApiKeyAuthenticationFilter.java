package com.leadrush.apikey.service;

import com.leadrush.apikey.entity.ApiKey;
import com.leadrush.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// X-API-Key authentication. Runs before the JWT filter; absence falls through to JWT.
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String presented = request.getHeader(HEADER);
        if (presented == null || presented.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> resolved = apiKeyService.resolveActive(presented);
        if (resolved.isEmpty()) {
            log.debug("Rejected X-API-Key — unknown or revoked");
            chain.doFilter(request, response);
            return;
        }

        ApiKey apiKey = resolved.get();

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_API_KEY"));
        var auth = new UsernamePasswordAuthenticationToken(apiKey.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        TenantContext.set(apiKey.getWorkspaceId(), apiKey.getUserId());

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
