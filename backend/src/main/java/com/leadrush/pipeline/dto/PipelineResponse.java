package com.leadrush.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineResponse {

    private UUID id;
    private String name;
    private String description;
    private boolean isDefault;
    private int displayOrder;

    private List<StageResponse> stages;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StageResponse {
        private UUID id;
        private String name;
        private String color;
        private int winProbability;
        private int displayOrder;
        private String stageType;
    }
}
