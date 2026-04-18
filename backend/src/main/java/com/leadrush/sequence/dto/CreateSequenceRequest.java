package com.leadrush.sequence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSequenceRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private UUID defaultMailboxId;
}
