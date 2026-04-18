package com.leadrush.task.repository;

import com.leadrush.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
