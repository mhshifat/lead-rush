package com.leadrush.apikey.service;

import com.leadrush.apikey.dto.ApiKeyResponse;
import com.leadrush.apikey.dto.CreateApiKeyRequest;
import com.leadrush.apikey.entity.ApiKey;
import com.leadrush.apikey.repository.ApiKeyRepository;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API key lifecycle + lookup.
 *
 * Plaintext keys look like "lr_<32 hex chars>" (~36 chars). We store only the SHA-256 hex,
 * so the DB being leaked doesn't hand over working credentials.
 *
 * The filter calls {@link #resolveActive(String)} on every request — that method is the hot path
 * and updates last_used_at asynchronously so the auth check stays cheap.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String PREFIX = "lr_";
    private static final int RANDOM_BYTES = 16; // 32 hex chars
    private static final SecureRandom RNG = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    // ── User-facing management ──

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        UUID wsId = TenantContext.getWorkspaceId();
        return apiKeyRepository.findByWorkspaceIdOrderByCreatedAtDesc(wsId).stream()
                .map(this::toListResponse)
                .toList();
    }

    /**
     * Create a key. Returns the plaintext exactly once in the response — we can never
     * show it again after this.
     */
    @Transactional
    public ApiKeyResponse create(CreateApiKeyRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();

        String plaintext = generatePlaintext();
        String hash = sha256(plaintext);
        String shown = plaintext.substring(PREFIX.length(), PREFIX.length() + 4);

        ApiKey apiKey = ApiKey.builder()
                .workspaceId(wsId)
                .userId(userId)
                .name(request.getName().trim())
                .keyHash(hash)
                .keyPrefix(shown)
                .build();
        apiKey = apiKeyRepository.save(apiKey);

        log.info("API key created: {} (user={}, workspace={})", apiKey.getId(), userId, wsId);

        ApiKeyResponse response = toListResponse(apiKey);
        response.setPlaintext(plaintext);
        return response;
    }

    @Transactional
    public void revoke(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        ApiKey apiKey = apiKeyRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
        if (apiKey.getRevokedAt() != null) return;
        apiKey.setRevokedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    // ── Filter lookup ──

    /**
     * Resolve a plaintext key to an active ApiKey. Called on every extension request.
     * Updates last_used_at so users can see stale keys in the settings UI.
     */
    @Transactional
    public Optional<ApiKey> resolveActive(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || !plaintext.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256(plaintext);
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash);
        found.ifPresent(k -> {
            k.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(k);
        });
        return found;
    }

    // ── Helpers ──

    private static String generatePlaintext() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RNG.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private ApiKeyResponse toListResponse(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .keyPrefix(apiKey.getKeyPrefix())
                .lastUsedAt(apiKey.getLastUsedAt())
                .revokedAt(apiKey.getRevokedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
