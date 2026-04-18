package com.leadrush.apikey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    /** Plaintext — only populated on creation response. Null on every list. */
    private String plaintext;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
