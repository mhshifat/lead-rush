package com.leadrush.leadscoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload for creating or updating a scoring rule.
 *
 * Trigger is required; condition fields are all optional (no condition = fires for every event).
 */
@Data
public class LeadScoreRuleRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Trigger type is required")
    private String triggerType;  // matches TriggerType enum names

    // Optional condition
    private String conditionField;
    private String conditionOperator;  // matches ConditionOperator enum names
    private String conditionValue;

    @NotNull(message = "Points is required")
    private Integer points;

    private Boolean enabled;
}
