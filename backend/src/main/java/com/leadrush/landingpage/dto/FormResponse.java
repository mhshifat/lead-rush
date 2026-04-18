package com.leadrush.landingpage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormResponse {
    private UUID id;
    private String name;
    private String description;

    /** Returned as raw JSON so frontend gets the object/array instead of a JSON-stringified string. */
    @JsonRawValue
    private String fields;

    private String successRedirectUrl;
    private String successMessage;
    private UUID autoEnrollSequenceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
