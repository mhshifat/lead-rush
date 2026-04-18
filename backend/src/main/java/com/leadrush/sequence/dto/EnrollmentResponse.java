package com.leadrush.sequence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrollmentResponse {

    private UUID id;
    private UUID sequenceId;
    private String sequenceName;
    private UUID contactId;
    private String contactFullName;
    private String contactEmail;
    private UUID mailboxId;
    private String mailboxEmail;

    private int currentStepIndex;
    private LocalDateTime nextExecutionAt;

    private String status;
    private LocalDateTime enrolledAt;
    private LocalDateTime completedAt;
}
