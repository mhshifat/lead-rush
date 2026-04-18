package com.leadrush.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealResponse {

    private UUID id;
    private String name;
    private String description;

    private UUID pipelineId;
    private UUID pipelineStageId;
    private String stageName;

    private BigDecimal valueAmount;
    private String valueCurrency;
    private UUID ownerUserId;

    private LocalDate expectedCloseAt;
    private LocalDateTime closedAt;

    private List<ContactSummary> contacts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactSummary {
        private UUID id;
        private String fullName;
        private String primaryEmail;
    }
}
