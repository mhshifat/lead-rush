package com.leadrush.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubjectSuggestionsRequest {

    /** Original subject or a short topic — the LLM generates variants from this. */
    @NotBlank(message = "subject is required")
    private String subject;

    /** Optional body to give the LLM context on what the email is about. */
    private String bodyPreview;

    /** How many variants to return. Defaults to 5, capped at 10. */
    private Integer count;
}
