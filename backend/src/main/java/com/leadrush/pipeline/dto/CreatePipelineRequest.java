package com.leadrush.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePipelineRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    private Boolean isDefault;
}
