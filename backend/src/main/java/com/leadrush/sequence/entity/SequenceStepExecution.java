package com.leadrush.sequence.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log of every step execution.
 * Records whether the step succeeded, failed, when it was sent, and tracking data.
 */
@Entity
@Table(name = "sequence_step_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SequenceStepExecution extends TenantEntity {

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "sequence_step_id", nullable = false)
    private UUID sequenceStepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "message_id", length = 500)
    private String messageId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "first_clicked_at")
    private LocalDateTime firstClickedAt;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    public enum ExecutionStatus {
        PENDING,    // About to execute
        SENT,       // Successfully sent
        FAILED,     // Send failed
        SKIPPED     // Skipped (e.g., DELAY step)
    }
}
