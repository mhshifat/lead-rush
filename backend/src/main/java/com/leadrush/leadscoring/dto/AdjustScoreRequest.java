package com.leadrush.leadscoring.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Manual score adjustment — "+20 points because the prospect attended our webinar".
 */
@Data
public class AdjustScoreRequest {

    @NotNull(message = "Points delta is required")
    private Integer pointsDelta;

    private String reason;
}
