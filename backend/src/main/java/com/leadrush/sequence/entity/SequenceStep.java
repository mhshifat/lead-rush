package com.leadrush.sequence.entity;

import com.leadrush.common.TenantEntity;
import com.leadrush.email.entity.EmailTemplate;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single step within a sequence.
 *
 * step_order determines the execution order (1, 2, 3, ...).
 * step_type determines what happens:
 *   EMAIL    → send an email (uses email_template_id or inline body)
 *   DELAY    → wait delay_days before next step
 *   CALL/TASK → create a task for the user
 */
@Entity
@Table(name = "sequence_steps",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sequence_id", "step_order"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sequence", "emailTemplate"})
public class SequenceStep extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sequence_id", nullable = false)
    private Sequence sequence;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private StepType stepType;

    @Column(name = "delay_days")
    @Builder.Default
    private int delayDays = 0;

    // ── EMAIL step config ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_template_id")
    private EmailTemplate emailTemplate;

    @Column(name = "subject_override", length = 500)
    private String subjectOverride;

    @Column(name = "body_html_override", columnDefinition = "text")
    private String bodyHtmlOverride;

    // ── TASK/CALL step config ──

    @Column(name = "task_description", columnDefinition = "text")
    private String taskDescription;

    // ── Conditional branching ──
    // Skip this step at execution time if a previous EMAIL step was opened / clicked.
    // Typical drip pattern: "follow-up #3 — skip if they already opened #1 or #2".

    @Column(name = "skip_if_previous_opened", nullable = false)
    @Builder.Default
    private boolean skipIfPreviousOpened = false;

    @Column(name = "skip_if_previous_clicked", nullable = false)
    @Builder.Default
    private boolean skipIfPreviousClicked = false;
}
