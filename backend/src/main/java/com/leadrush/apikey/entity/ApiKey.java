package com.leadrush.apikey.entity;

import com.leadrush.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A long-lived token used by external clients (browser extension, future Zapier-style integrations)
 * instead of the 15-min JWT access token.
 *
 * NOT TenantEntity — we look it up from a request header BEFORE tenant context is established.
 * The workspaceId is stored explicitly and used to populate TenantContext once the key resolves.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"keyHash"})
public class ApiKey extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /** The user whose permissions this key inherits. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    /** SHA-256 hex of the plaintext key. Never store the plaintext. */
    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    /** First few chars of the plaintext — shown to the user to identify the key. */
    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
