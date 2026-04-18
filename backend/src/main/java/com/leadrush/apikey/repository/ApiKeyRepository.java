package com.leadrush.apikey.repository;

import com.leadrush.apikey.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Called on every extension request — indexed on `key_hash WHERE revoked_at IS NULL`. */
    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    /** Listing for the settings page. */
    List<ApiKey> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<ApiKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
