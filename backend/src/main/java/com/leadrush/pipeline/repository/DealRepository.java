package com.leadrush.pipeline.repository;

import com.leadrush.pipeline.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    List<Deal> findByWorkspaceIdAndPipelineIdOrderByCreatedAtDesc(UUID workspaceId, UUID pipelineId);

    Optional<Deal> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Deal> findByWorkspaceIdAndContactsId(UUID workspaceId, UUID contactId);

    long countByWorkspaceId(UUID workspaceId);

    /**
     * Per-stage aggregate for a pipeline: stageId, count, sum(valueAmount).
     * Powers the pipeline report. Null valueAmount is treated as 0 via COALESCE.
     */
    @Query("""
        SELECT d.stage.id,
               COUNT(d),
               COALESCE(SUM(d.valueAmount), 0)
        FROM Deal d
        WHERE d.workspaceId = :workspaceId AND d.pipelineId = :pipelineId
        GROUP BY d.stage.id
    """)
    List<Object[]> aggregateByStage(@Param("workspaceId") UUID workspaceId,
                                     @Param("pipelineId") UUID pipelineId);
}
