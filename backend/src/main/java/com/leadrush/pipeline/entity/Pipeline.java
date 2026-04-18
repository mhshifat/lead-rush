package com.leadrush.pipeline.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline — a stack of stages that deals progress through.
 * A workspace can have multiple pipelines (e.g., "New Business Sales", "Renewals").
 */
@Entity
@Table(name = "pipelines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"stages"})
public class Pipeline extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<PipelineStage> stages = new ArrayList<>();

    public void addStage(PipelineStage stage) {
        stages.add(stage);
        stage.setPipeline(this);
    }
}
