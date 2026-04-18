package com.leadrush.pipeline.repository;

import com.leadrush.pipeline.entity.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    List<Pipeline> findByWorkspaceIdOrderByDisplayOrderAsc(UUID workspaceId);

    Optional<Pipeline> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Pipeline> findFirstByWorkspaceIdAndIsDefaultTrue(UUID workspaceId);
}
