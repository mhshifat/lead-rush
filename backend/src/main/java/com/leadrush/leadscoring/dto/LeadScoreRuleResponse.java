package com.leadrush.leadscoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadScoreRuleResponse {

    private UUID id;
    private String name;
    private String description;
    private String triggerType;
    private String conditionField;
    private String conditionOperator;
    private String conditionValue;
    private int points;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
