package com.leadrush.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Task surface the extension uses — only LinkedIn-relevant fields.
 * Includes the contact's LinkedIn URL so the content script can match tasks to
 * the profile page the user is viewing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtensionTaskResponse {

    private UUID id;
    private String type;          // LINKEDIN_CONNECT / LINKEDIN_MESSAGE
    private String title;
    private String description;   // suggested copy

    private UUID contactId;
    private String contactName;
    private String contactTitle;
    private String contactCompany;
    private String contactLinkedinUrl;

    private LocalDateTime dueAt;
}
