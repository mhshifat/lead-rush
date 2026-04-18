package com.leadrush.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

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
}
