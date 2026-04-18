package com.leadrush.landingpage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateLandingPageRequest {
    @NotBlank(message = "Name is required")
    private String name;

    /** Optional — auto-generated from name if not provided. */
    private String slug;

    private String metaTitle;
    private String metaDescription;

    /** JSON blocks array. Passed as string. */
    private String blocks;
}
