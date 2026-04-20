package com.leadrush.extension.repository;

import com.leadrush.extension.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<SavedSearch> findByWorkspaceIdAndUrl(UUID workspaceId, String url);

    Optional<SavedSearch> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
