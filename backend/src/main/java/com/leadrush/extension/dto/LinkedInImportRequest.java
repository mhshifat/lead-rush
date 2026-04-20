package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Payload the extension sends when the user clicks "Import to Lead Rush" on a profile.
 * All fields are optional except the LinkedIn URL — some profiles hide names / titles.
 *
 * Deep-scrape fields (about, experiences, education, skills) are best-effort;
 * the backend merges them into {@code contact.metadata.linkedin.*} so we never
 * lose data the user already curated in Lead Rush.
 */
@Data
public class LinkedInImportRequest {

    @NotBlank(message = "linkedinUrl is required")
    private String linkedinUrl;

    private String firstName;
    private String lastName;
    private String title;
    private String companyName;
    private String avatarUrl;
    private String location;

    /**
     * When set, skip the "match by linkedinUrl" dedupe and merge the scraped
     * fields into this specific contact instead. Used by the duplicate-detector
     * flow: user previously imported "Jane Doe" with no LinkedIn URL, now visits
     * her LinkedIn and chooses "Merge into existing contact". This attaches the
     * LinkedIn URL to that record rather than creating a sibling.
     */
    private UUID mergeIntoContactId;

    /**
     * When set, immediately enroll the imported contact into this sequence.
     * Powers the panel's "Import & enroll" one-click flow right after the user
     * hits Connect on LinkedIn. Enrollment failures are returned on the
     * response's {@code enrollError} field — the import itself still succeeds.
     */
    private UUID autoEnrollSequenceId;

    // ── Deep-scrape fields (optional) ──

    private String about;
    private List<Experience> experiences;
    private List<Education> education;
    private List<String> skills;

    @Data
    public static class Experience {
        private String title;
        private String companyName;
        private String dateRange;
    }

    @Data
    public static class Education {
        private String school;
        private String degree;
        private String fieldOfStudy;
    }
}
