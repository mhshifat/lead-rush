package com.leadrush.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Per-step drop-off for a sequence.
 * First step is the one at stepOrder = 1, last step is the last configured step.
 * Steps the engine has never reached appear with all-zero counts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceFunnel {

    private UUID sequenceId;
    private String sequenceName;
    private List<Step> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private UUID stepId;
        private int stepOrder;
        private String stepType;
        private String label;           // short label for display (e.g., "Email 1", "Task", "Delay")

        private long sent;
        private long opened;
        private long clicked;
        private long replied;
        private long skipped;
        private long failed;

        private double openRate;        // opened / sent
        private double clickRate;       // clicked / sent
        private double replyRate;       // replied / sent
    }
}
