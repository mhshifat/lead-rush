package com.leadrush.task.repository;

import com.leadrush.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<Task> findByWorkspaceIdAndContactId(UUID workspaceId, UUID contactId);

    Page<Task> findByWorkspaceIdAndStatus(UUID workspaceId, Task.TaskStatus status, Pageable pageable);

    long countByWorkspaceIdAndStatus(UUID workspaceId, Task.TaskStatus status);

    /**
     * Pending tasks of specific types for a workspace — used by the extension to list
     * pending LinkedIn actions (LINKEDIN_CONNECT, LINKEDIN_MESSAGE).
     */
    List<Task> findByWorkspaceIdAndTaskTypeInAndStatusOrderByDueAtAsc(
            UUID workspaceId, List<Task.TaskType> types, Task.TaskStatus status);

    /** Pending tasks of certain types for a specific contact — profile-page lookup. */
    List<Task> findByWorkspaceIdAndContactIdAndTaskTypeInAndStatus(
            UUID workspaceId, UUID contactId, List<Task.TaskType> types, Task.TaskStatus status);

    /**
     * Recent teammate touches on this contact — any task (note, LinkedIn action,
     * completed outreach) assigned to someone OTHER than the current user,
     * created in the given time window. Used to raise collision warnings in
     * the LinkedIn side panel so two reps don't dial the same prospect an hour apart.
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.workspaceId = :wsId
              AND t.contactId = :contactId
              AND t.assignedToUserId IS NOT NULL
              AND t.assignedToUserId <> :excludeUserId
              AND t.createdAt >= :since
            ORDER BY t.createdAt DESC
            """)
    List<Task> findRecentTeammateTouches(
            @Param("wsId") UUID workspaceId,
            @Param("contactId") UUID contactId,
            @Param("excludeUserId") UUID excludeUserId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}
