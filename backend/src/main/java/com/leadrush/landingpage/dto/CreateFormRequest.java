package com.leadrush.landingpage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFormRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    /** JSON array of field definitions — passed as string. */
    private String fields;

    private String successRedirectUrl;
    private String successMessage;
    private UUID autoEnrollSequenceId;
}
