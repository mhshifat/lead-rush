package com.leadrush.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Deals grouped by stage for a single pipeline.
 * Sum of values is in the pipeline's currency — we assume a single currency per pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineReport {

    private UUID pipelineId;
    private String pipelineName;
    private long totalDeals;
    private BigDecimal totalValue;
    private List<StageBreakdown> stages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageBreakdown {
        private UUID stageId;
        private String stageName;
        private int stageOrder;
        private String color;
        private Integer probability;   // 0..100

        private long dealCount;
        private BigDecimal totalValue;
    }
}
