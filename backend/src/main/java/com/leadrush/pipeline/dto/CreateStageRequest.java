package com.leadrush.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStageRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String color;
    private Integer winProbability;
    private String stageType;   // OPEN, WON, LOST
}
