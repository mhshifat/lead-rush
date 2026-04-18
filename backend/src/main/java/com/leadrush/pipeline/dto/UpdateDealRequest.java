package com.leadrush.pipeline.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Update deal — all fields optional. Only non-null fields are applied.
 */
@Data
public class UpdateDealRequest {
    private String name;
    private String description;
    private BigDecimal valueAmount;
    private String valueCurrency;
    private LocalDate expectedCloseAt;
    private UUID pipelineStageId;       // Move to a different stage
}
