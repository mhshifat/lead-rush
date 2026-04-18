package com.leadrush.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class GenerateEmailRequest {

    /** Contact to personalize for — the service pulls first name, title, company, etc. */
    @NotNull(message = "contactId is required")
    private UUID contactId;

    /**
     * One-line pitch / value prop the LLM should weave into the email.
     * e.g., "We help mid-market SaaS companies cut churn by 20%"
     */
    private String valueProp;

    /** Optional — shapes tone (e.g., "friendly", "concise", "formal"). */
    private String tone;

    /** Optional — shapes length (SHORT | MEDIUM | LONG). Defaults to MEDIUM. */
    private String length;
}
