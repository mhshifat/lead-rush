package com.leadrush.security;

import com.leadrush.config.LeadRushProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final LeadRushProperties properties;
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        signingKey = Keys.hmacShaKeyFor(
            properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateAccessToken(UUID userId, String email, UUID workspaceId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getJwt().getAccessTokenExpiry());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("workspaceId", workspaceId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getJwt().getRefreshTokenExpiry());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("tokenId", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? UUID.fromString(claims.getSubject()) : null;
    }

    public boolean isTokenValid(String token) {
        return parseToken(token) != null;
    }
}
