package com.leadrush.sequence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceResponse {

    private UUID id;
    private String name;
    private String description;
    private String status;

    private UUID defaultMailboxId;
    private String defaultMailboxEmail;

    private int totalEnrolled;
    private int totalCompleted;
    private int totalReplied;

    private List<StepResponse> steps;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StepResponse {
        private UUID id;
        private int stepOrder;
        private String stepType;
        private int delayDays;

        private UUID emailTemplateId;
        private String emailTemplateName;
        private String subjectOverride;
        private String bodyHtmlOverride;

        private String taskDescription;

        private boolean skipIfPreviousOpened;
        private boolean skipIfPreviousClicked;
    }
}
