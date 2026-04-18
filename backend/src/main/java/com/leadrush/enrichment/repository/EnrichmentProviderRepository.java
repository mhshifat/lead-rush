package com.leadrush.enrichment.repository;

import com.leadrush.enrichment.entity.EnrichmentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrichmentProviderRepository extends JpaRepository<EnrichmentProvider, UUID> {

    List<EnrichmentProvider> findByWorkspaceIdOrderByPriorityAsc(UUID workspaceId);

    List<EnrichmentProvider> findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(UUID workspaceId);

    Optional<EnrichmentProvider> findByWorkspaceIdAndProviderKey(UUID workspaceId, String providerKey);

    Optional<EnrichmentProvider> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
