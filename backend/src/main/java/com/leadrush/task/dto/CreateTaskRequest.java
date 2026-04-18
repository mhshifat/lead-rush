package com.leadrush.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String taskType;            // MANUAL, CALL, LINKEDIN_MESSAGE, LINKEDIN_CONNECT
    private UUID contactId;
    private LocalDateTime dueAt;
}
