package com.leadrush.landingpage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Public form submission — hit from landing page / embedded widget.
 * No JWT required (public endpoint).
 */
@Data
public class PublicSubmitRequest {

    @NotNull(message = "Form ID is required")
    private UUID formId;

    /** Optional — set when submission came from a specific landing page. */
    private UUID landingPageId;

    /** Submitted field data: { "firstName": "John", "email": "..." } */
    @NotNull(message = "Data is required")
    private Map<String, Object> data;

    // Attribution (frontend should set these from URL query params)
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
}
