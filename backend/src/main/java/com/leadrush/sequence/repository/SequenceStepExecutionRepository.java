package com.leadrush.sequence.repository;

import com.leadrush.sequence.entity.SequenceStepExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SequenceStepExecutionRepository extends JpaRepository<SequenceStepExecution, UUID> {

    List<SequenceStepExecution> findByEnrollmentIdOrderByCreatedAtAsc(UUID enrollmentId);

    // ── Workspace-wide analytics (last N days) ──

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e
        WHERE e.workspaceId = :workspaceId
        AND e.status = com.leadrush.sequence.entity.SequenceStepExecution.ExecutionStatus.SENT
        AND e.sentAt >= :since
    """)
    long countSentByWorkspaceSince(@Param("workspaceId") UUID workspaceId, @Param("since") LocalDateTime since);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e
        WHERE e.workspaceId = :workspaceId AND e.openedAt >= :since
    """)
    long countOpenedByWorkspaceSince(@Param("workspaceId") UUID workspaceId, @Param("since") LocalDateTime since);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e
        WHERE e.workspaceId = :workspaceId AND e.firstClickedAt >= :since
    """)
    long countClickedByWorkspaceSince(@Param("workspaceId") UUID workspaceId, @Param("since") LocalDateTime since);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e
        WHERE e.workspaceId = :workspaceId AND e.repliedAt >= :since
    """)
    long countRepliedByWorkspaceSince(@Param("workspaceId") UUID workspaceId, @Param("since") LocalDateTime since);

    // ── Per-sequence analytics (all time) ──

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e, SequenceEnrollment en
        WHERE e.enrollmentId = en.id AND en.sequence.id = :sequenceId
        AND e.status = com.leadrush.sequence.entity.SequenceStepExecution.ExecutionStatus.SENT
    """)
    long countSentBySequence(@Param("sequenceId") UUID sequenceId);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e, SequenceEnrollment en
        WHERE e.enrollmentId = en.id AND en.sequence.id = :sequenceId
        AND e.openedAt IS NOT NULL
    """)
    long countOpenedBySequence(@Param("sequenceId") UUID sequenceId);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e, SequenceEnrollment en
        WHERE e.enrollmentId = en.id AND en.sequence.id = :sequenceId
        AND e.firstClickedAt IS NOT NULL
    """)
    long countClickedBySequence(@Param("sequenceId") UUID sequenceId);

    @Query("""
        SELECT COUNT(e) FROM SequenceStepExecution e, SequenceEnrollment en
        WHERE e.enrollmentId = en.id AND en.sequence.id = :sequenceId
        AND e.repliedAt IS NOT NULL
    """)
    long countRepliedBySequence(@Param("sequenceId") UUID sequenceId);

    // ── Per-step funnel ──
    // Single aggregate query that groups executions by step within one sequence.
    // Each row returns: stepId, count(sent), count(opened), count(clicked), count(replied).
    @Query("""
        SELECT e.sequenceStepId,
               SUM(CASE WHEN e.status = com.leadrush.sequence.entity.SequenceStepExecution.ExecutionStatus.SENT THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.status = com.leadrush.sequence.entity.SequenceStepExecution.ExecutionStatus.SKIPPED THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.status = com.leadrush.sequence.entity.SequenceStepExecution.ExecutionStatus.FAILED THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.openedAt IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.firstClickedAt IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.repliedAt IS NOT NULL THEN 1 ELSE 0 END)
        FROM SequenceStepExecution e, SequenceEnrollment en
        WHERE e.enrollmentId = en.id AND en.sequence.id = :sequenceId
        GROUP BY e.sequenceStepId
    """)
    List<Object[]> funnelBySequence(@Param("sequenceId") UUID sequenceId);
}
