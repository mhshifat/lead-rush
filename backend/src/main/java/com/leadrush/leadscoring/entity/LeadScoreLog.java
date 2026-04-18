package com.leadrush.leadscoring.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Audit trail of every score change.
 *
 * Answers: "Why is this contact's score 85?" → click through the logs to see
 * every rule that fired, when, and what points were applied.
 */
@Entity
@Table(name = "lead_score_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadScoreLog extends TenantEntity {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    /** FK to the rule that fired. Null for manual adjustments. */
    @Column(name = "rule_id")
    private UUID ruleId;

    /** Snapshot of the rule's name — survives rule deletion. */
    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Column(name = "score_before", nullable = false)
    private int scoreBefore;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type")
    private TriggerType triggerType;

    @Column(columnDefinition = "text")
    private String reason;
}
