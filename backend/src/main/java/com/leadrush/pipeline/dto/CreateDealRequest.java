package com.leadrush.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateDealRequest {

    @NotBlank(message = "Deal name is required")
    private String name;

    @NotNull(message = "Pipeline ID is required")
    private UUID pipelineId;

    private UUID pipelineStageId;           // Optional — defaults to first stage

    private String description;
    private BigDecimal valueAmount;
    private String valueCurrency;           // e.g., "USD", "EUR"
    private LocalDate expectedCloseAt;
    private List<UUID> contactIds;          // contacts to associate with this deal
}
