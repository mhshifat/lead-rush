package com.leadrush.leadscoring.repository;

import com.leadrush.leadscoring.entity.LeadScoreRule;
import com.leadrush.leadscoring.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadScoreRuleRepository extends JpaRepository<LeadScoreRule, UUID> {

    /** All rules in a workspace, newest first. */
    List<LeadScoreRule> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    /** Enabled rules matching a trigger for a workspace — hot path called on every scoring event. */
    List<LeadScoreRule> findByWorkspaceIdAndTriggerTypeAndEnabledTrue(UUID workspaceId, TriggerType triggerType);

    /** Safe lookup scoped to a workspace (never match other tenants' rule IDs). */
    Optional<LeadScoreRule> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
