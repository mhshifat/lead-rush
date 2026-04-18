package com.leadrush.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One event in a contact's activity timeline.
 *
 * Types:
 *   EMAIL_SENT      — we sent them an email (via sequence)
 *   EMAIL_OPENED    — they opened the email
 *   EMAIL_CLICKED   — they clicked a link in the email
 *   EMAIL_REPLIED   — they replied (Phase 3 — requires IMAP reply monitoring)
 *   UNSUBSCRIBED    — they unsubscribed
 *   ENROLLED        — they were enrolled in a sequence
 *   SEQUENCE_COMPLETED
 *   TASK_CREATED    — a task was created for this contact
 *   TASK_COMPLETED  — a task was completed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityEvent {
    private UUID id;
    private String type;
    private String title;
    private String description;
    private UUID sequenceId;
    private String sequenceName;
    private UUID stepExecutionId;
    private LocalDateTime occurredAt;
}
