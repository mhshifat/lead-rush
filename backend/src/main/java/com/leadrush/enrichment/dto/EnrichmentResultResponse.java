package com.leadrush.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichmentResultResponse {

    private UUID id;
    private UUID contactId;
    private String providerKey;
    private String status;

    private String foundEmail;
    private String foundPhone;
    private String foundTitle;
    private String foundLinkedinUrl;
    private Integer confidenceScore;

    private String errorMessage;
    private LocalDateTime enrichedAt;
}
