package com.leadrush.task.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A task — manual action for a user to take (call contact, send LinkedIn DM, etc.).
 *
 * Tasks are created:
 *   - Automatically by sequences when a CALL/TASK/LINKEDIN_* step executes
 *   - Manually by users in the task list UI
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends TenantEntity {

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    @Builder.Default
    private TaskType taskType = TaskType.MANUAL;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "sequence_id")
    private UUID sequenceId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum TaskType {
        MANUAL,                 // Created by user
        CALL,                   // Phone call task
        LINKEDIN_MESSAGE,       // Send a LinkedIn DM (manual for now; future: browser extension)
        LINKEDIN_CONNECT,       // Send a LinkedIn connection request
        NOTE                    // A written observation — always stored as COMPLETED
    }

    public enum TaskStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }
}
