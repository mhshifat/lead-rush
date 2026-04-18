package com.leadrush.leadscoring.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * User-defined scoring rule.
 *
 * "When TRIGGER fires, if optional CONDITION matches the contact, add POINTS to the score."
 *
 * Example: "When EMAIL_OPENED and contact.title CONTAINS 'CEO', add 10 points."
 * Example: "When CONTACT_CREATED (no condition), add 5 points."
 * Example: "When FORM_SUBMITTED, add 20 points."
 */
@Entity
@Table(name = "lead_score_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadScoreRule extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    /** Field on the Contact to check (e.g., "title", "lifecycleStage"). Null = no condition. */
    @Column(name = "condition_field")
    private String conditionField;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_operator")
    private ConditionOperator conditionOperator;

    /** Value to compare against. */
    @Column(name = "condition_value")
    private String conditionValue;

    /** Score delta to apply. Can be negative (e.g., -10 for "unsubscribed"). */
    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
