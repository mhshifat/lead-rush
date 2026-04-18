package com.leadrush.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {

    private UUID id;
    private UUID assignedToUserId;

    private String title;
    private String description;
    private String taskType;

    private UUID contactId;
    private String contactFullName;

    private UUID sequenceId;
    private String sequenceName;

    private LocalDateTime dueAt;
    private String status;
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
