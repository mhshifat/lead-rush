package com.leadrush.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkedInImportResponse {
    private UUID contactId;
    private String fullName;
    /** true if we created a new contact, false if we found + updated an existing one. */
    private boolean created;

    // ── Populated only when {@code autoEnrollSequenceId} was provided on the request ──

    private UUID enrollmentId;
    private String enrolledSequenceName;
    /** Non-null on partial success — contact imported, but enroll failed (e.g. no mailbox). */
    private String enrollError;

    /**
     * Job/company changes detected when this import overwrote a previously-stored
     * title or companyName. Empty list when nothing changed or this is a first-time
     * import. Used by the panel to surface a "Job change detected" banner.
     */
    private List<JobChangeEvent> jobChanges;
}
