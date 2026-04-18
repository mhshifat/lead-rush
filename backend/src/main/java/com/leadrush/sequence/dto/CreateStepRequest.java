package com.leadrush.sequence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateStepRequest {

    @NotBlank(message = "Step type is required")
    private String stepType;        // EMAIL, DELAY, CALL, TASK, LINKEDIN_MESSAGE, LINKEDIN_CONNECT

    private Integer delayDays;

    // EMAIL step fields
    private UUID emailTemplateId;
    private String subjectOverride;
    private String bodyHtmlOverride;

    // TASK/CALL step fields
    private String taskDescription;

    // Conditional branching (EMAIL steps)
    private Boolean skipIfPreviousOpened;
    private Boolean skipIfPreviousClicked;
}
