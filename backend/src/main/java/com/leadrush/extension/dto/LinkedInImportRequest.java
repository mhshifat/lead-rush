package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload the extension sends when the user clicks "Import to Lead Rush" on a profile.
 * All fields are optional except the LinkedIn URL — some profiles hide names / titles.
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
}
