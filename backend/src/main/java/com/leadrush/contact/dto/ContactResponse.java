package com.leadrush.contact.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contact response DTO — what the API returns to the frontend.
 * Flattened version of the Contact entity (no lazy-loaded relationships).
 *
 * Remember: Jackson serializes this as camelCase JSON automatically.
 *   companyName → "companyName" in JSON (not "company_name")
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String title;

    // Company info (flattened — no nested Company object)
    private UUID companyId;
    private String companyName;

    private String lifecycleStage;
    private int leadScore;
    private String source;

    private String avatarUrl;
    private String website;
    private String linkedinUrl;
    private String twitterUrl;

    private String primaryEmail;
    private String primaryPhone;

    private List<EmailResponse> emails;
    private List<PhoneResponse> phones;
    private List<TagResponse> tags;

    private LocalDateTime lastContactedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailResponse {
        private UUID id;
        private String email;
        private String emailType;
        private boolean primary;
        /** VERIFIED | LIKELY | UNKNOWN | GUESSED | VALID | INVALID | CATCH_ALL */
        private String verificationStatus;
        /** Adapter key that produced this row (HUNTER, PATTERN_CACHE, …), or null if user-entered. */
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneResponse {
        private UUID id;
        private String phone;
        private String phoneType;
        private boolean primary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagResponse {
        private UUID id;
        private String name;
        private String color;
    }
}
