package com.leadrush.webhook.repository;

import com.leadrush.webhook.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    /** All endpoints in a workspace — used by the settings UI. */
    List<WebhookEndpoint> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    /** Enabled endpoints — used by the publisher to fan out. */
    List<WebhookEndpoint> findByWorkspaceIdAndEnabledTrue(UUID workspaceId);

    Optional<WebhookEndpoint> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
