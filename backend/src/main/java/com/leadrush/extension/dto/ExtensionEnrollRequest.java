package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * POST /api/v1/ext/enrollments — enroll a contact into a sequence from the
 * extension. We don't let the extension pick a mailbox; it falls back to the
 * sequence's default mailbox. If none is set, the enroll rejects with 400.
 */
@Data
public class ExtensionEnrollRequest {
    @NotNull
    private UUID sequenceId;

    @NotNull
    private UUID contactId;
}
