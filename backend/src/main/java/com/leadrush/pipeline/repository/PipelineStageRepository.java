package com.leadrush.pipeline.repository;

import com.leadrush.pipeline.entity.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

    List<PipelineStage> findByPipelineIdOrderByDisplayOrderAsc(UUID pipelineId);

    Optional<PipelineStage> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
