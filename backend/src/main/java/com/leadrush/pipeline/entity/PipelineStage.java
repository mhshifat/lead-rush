package com.leadrush.pipeline.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * PipelineStage — a column in the Kanban board (e.g., "Discovery", "Demo", "Closed Won").
 */
@Entity
@Table(name = "pipeline_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"pipeline"})
public class PipelineStage extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @Column(nullable = false)
    private String name;

    private String color;       // hex: "#3B82F6"

    @Column(name = "win_probability", nullable = false)
    @Builder.Default
    private int winProbability = 0;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false)
    @Builder.Default
    private StageType stageType = StageType.OPEN;

    public enum StageType {
        OPEN,       // Deal is in progress
        WON,        // Closed-won (deal completed successfully)
        LOST        // Closed-lost (deal lost)
    }
}
