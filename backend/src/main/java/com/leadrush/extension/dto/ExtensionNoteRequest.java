package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /api/v1/ext/contacts/{id}/notes — drop a freeform observation against
 * a contact from the LinkedIn panel. Persists as a pre-completed NOTE task so
 * it surfaces on the contact's activity timeline.
 */
@Data
public class ExtensionNoteRequest {
    @NotBlank
    @Size(max = 2000)
    private String body;
}
