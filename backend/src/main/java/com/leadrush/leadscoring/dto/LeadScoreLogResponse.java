package com.leadrush.leadscoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Single audit entry in a contact's score history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadScoreLogResponse {

    private UUID id;
    private UUID contactId;
    private UUID ruleId;
    private String ruleName;
    private int pointsDelta;
    private int scoreBefore;
    private int scoreAfter;
    private String triggerType;
    private String reason;
    private LocalDateTime createdAt;
}
