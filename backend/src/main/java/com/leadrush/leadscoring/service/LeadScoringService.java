package com.leadrush.leadscoring.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.leadscoring.dto.LeadScoreLogResponse;
import com.leadrush.leadscoring.dto.LeadScoreRuleRequest;
import com.leadrush.leadscoring.dto.LeadScoreRuleResponse;
import com.leadrush.leadscoring.entity.ConditionOperator;
import com.leadrush.leadscoring.entity.LeadScoreLog;
import com.leadrush.leadscoring.entity.LeadScoreRule;
import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.repository.LeadScoreLogRepository;
import com.leadrush.leadscoring.repository.LeadScoreRuleRepository;
import com.leadrush.notification.entity.NotificationType;
import com.leadrush.notification.service.NotificationService;
import com.leadrush.security.TenantContext;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lead scoring rule engine. Fires on triggers, evaluates conditions, mutates contact score. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadScoringService {

    private final LeadScoreRuleRepository ruleRepository;
    private final LeadScoreLogRepository logRepository;
    private final ContactRepository contactRepository;
    private final NotificationService notificationService;
    private final WebhookService webhookService;

    /** Milestones that trigger a notification when crossed upwards. */
    private static final int[] SCORE_THRESHOLDS = { 25, 50, 75, 100, 150, 200 };

    @Transactional(readOnly = true)
    public List<LeadScoreRuleResponse> listRules() {
        UUID wsId = TenantContext.getWorkspaceId();
        return ruleRepository.findByWorkspaceIdOrderByCreatedAtDesc(wsId).stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional
    public LeadScoreRuleResponse createRule(LeadScoreRuleRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();

        LeadScoreRule rule = LeadScoreRule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .triggerType(TriggerType.valueOf(request.getTriggerType()))
                .conditionField(request.getConditionField())
                .conditionOperator(request.getConditionOperator() != null && !request.getConditionOperator().isBlank()
                        ? ConditionOperator.valueOf(request.getConditionOperator()) : null)
                .conditionValue(request.getConditionValue())
                .points(request.getPoints())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
        rule.setWorkspaceId(wsId);
        return toRuleResponse(ruleRepository.save(rule));
    }

    @Transactional
    public LeadScoreRuleResponse updateRule(UUID id, LeadScoreRuleRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        LeadScoreRule rule = ruleRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("LeadScoreRule", id));

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setTriggerType(TriggerType.valueOf(request.getTriggerType()));
        rule.setConditionField(request.getConditionField());
        rule.setConditionOperator(request.getConditionOperator() != null && !request.getConditionOperator().isBlank()
                ? ConditionOperator.valueOf(request.getConditionOperator()) : null);
        rule.setConditionValue(request.getConditionValue());
        rule.setPoints(request.getPoints());
        if (request.getEnabled() != null) rule.setEnabled(request.getEnabled());

        return toRuleResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        LeadScoreRule rule = ruleRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("LeadScoreRule", id));
        ruleRepository.delete(rule);
    }

    @Transactional
    public void fireTrigger(TriggerType trigger, UUID contactId, String reason) {
        UUID wsId = TenantContext.getWorkspaceId();
        if (wsId == null) {
            log.warn("fireTrigger called without tenant context (trigger={}) — skipping", trigger);
            return;
        }
        fireTrigger(wsId, trigger, contactId, reason);
    }

    // Explicit-workspace variant for public endpoints (tracking pixel, unsubscribe, forms)
    // where no JWT is present. Never throws: scoring failures are logged and swallowed.
    @Transactional
    public void fireTrigger(UUID workspaceId, TriggerType trigger, UUID contactId, String reason) {
        try {
            Contact contact = contactRepository.findByIdAndWorkspaceId(contactId, workspaceId).orElse(null);
            if (contact == null) return;

            List<LeadScoreRule> rules = ruleRepository
                    .findByWorkspaceIdAndTriggerTypeAndEnabledTrue(workspaceId, trigger);
            if (rules.isEmpty()) return;

            for (LeadScoreRule rule : rules) {
                if (conditionMatches(rule, contact)) {
                    applyRule(workspaceId, rule, contact, trigger, reason);
                }
            }
        } catch (Exception e) {
            log.warn("Scoring trigger failed (trigger={}, contactId={}): {}", trigger, contactId, e.getMessage());
        }
    }

    @Transactional
    public void adjustScore(UUID contactId, int pointsDelta, String reason) {
        UUID wsId = TenantContext.getWorkspaceId();
        Contact contact = contactRepository.findByIdAndWorkspaceId(contactId, wsId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        int before = contact.getLeadScore();
        int after = before + pointsDelta;
        contact.setLeadScore(after);
        contactRepository.save(contact);

        LeadScoreLog logEntry = LeadScoreLog.builder()
                .contactId(contactId)
                .ruleId(null)
                .ruleName(null)
                .pointsDelta(pointsDelta)
                .scoreBefore(before)
                .scoreAfter(after)
                .triggerType(null)
                .reason(reason != null ? reason : "Manual adjustment")
                .build();
        logEntry.setWorkspaceId(wsId);
        logRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public List<LeadScoreLogResponse> getHistory(UUID contactId) {
        UUID wsId = TenantContext.getWorkspaceId();
        return logRepository.findByWorkspaceIdAndContactIdOrderByCreatedAtDesc(wsId, contactId).stream()
                .map(this::toLogResponse)
                .toList();
    }

    private void applyRule(UUID workspaceId, LeadScoreRule rule, Contact contact,
                            TriggerType trigger, String reason) {
        int before = contact.getLeadScore();
        int after = before + rule.getPoints();

        contact.setLeadScore(after);
        contactRepository.save(contact);

        LeadScoreLog logEntry = LeadScoreLog.builder()
                .contactId(contact.getId())
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .pointsDelta(rule.getPoints())
                .scoreBefore(before)
                .scoreAfter(after)
                .triggerType(trigger)
                .reason(reason)
                .build();
        logEntry.setWorkspaceId(workspaceId);
        logRepository.save(logEntry);

        log.debug("Applied rule '{}' to contact {}: {} -> {} ({}{})",
                rule.getName(), contact.getId(), before, after,
                rule.getPoints() >= 0 ? "+" : "", rule.getPoints());

        maybeNotifyThreshold(workspaceId, contact, before, after);
    }

    // Fires only on upward crossings so dips + re-cross don't spam the team.
    private void maybeNotifyThreshold(UUID workspaceId, Contact contact, int before, int after) {
        if (after <= before) return;
        for (int threshold : SCORE_THRESHOLDS) {
            if (before < threshold && after >= threshold) {
                notificationService.notifyWorkspace(
                        workspaceId,
                        NotificationType.SCORE_THRESHOLD,
                        "Lead score crossed " + threshold,
                        contact.getFullName() + " is now scoring " + after,
                        "/contacts/" + contact.getId(),
                        Map.of("contactId", contact.getId(), "threshold", threshold, "score", after)
                );

                webhookService.publish(workspaceId, WebhookEventType.LEAD_SCORE_THRESHOLD, Map.of(
                        "contactId", contact.getId(),
                        "contactName", contact.getFullName(),
                        "threshold", threshold,
                        "scoreBefore", before,
                        "scoreAfter", after
                ));
            }
        }
    }

    // No condition → always matches.
    private boolean conditionMatches(LeadScoreRule rule, Contact contact) {
        if (rule.getConditionField() == null || rule.getConditionOperator() == null) {
            return true;
        }

        String actual = extractFieldValue(contact, rule.getConditionField());
        String expected = rule.getConditionValue();

        if (actual == null) return false;
        if (expected == null) expected = "";

        return compare(actual, rule.getConditionOperator(), expected);
    }

    private String extractFieldValue(Contact contact, String field) {
        return switch (field) {
            case "title" -> contact.getTitle();
            case "firstName" -> contact.getFirstName();
            case "lastName" -> contact.getLastName();
            case "lifecycleStage" -> contact.getLifecycleStage() != null ? contact.getLifecycleStage().name() : null;
            case "source" -> contact.getSource() != null ? contact.getSource().name() : null;
            case "leadScore" -> String.valueOf(contact.getLeadScore());
            case "email" -> contact.getPrimaryEmail();
            case "website" -> contact.getWebsite();
            case "linkedinUrl" -> contact.getLinkedinUrl();
            case "companyName" -> contact.getCompany() != null ? contact.getCompany().getName() : null;
            case "companyDomain" -> contact.getCompany() != null ? contact.getCompany().getDomain() : null;
            case "companyIndustry" -> contact.getCompany() != null ? contact.getCompany().getIndustry() : null;
            default -> null;
        };
    }

    private boolean compare(String actual, ConditionOperator op, String expected) {
        String a = actual.toLowerCase();
        String e = expected.toLowerCase();
        return switch (op) {
            case EQUALS -> a.equals(e);
            case NOT_EQUALS -> !a.equals(e);
            case CONTAINS -> a.contains(e);
            case STARTS_WITH -> a.startsWith(e);
            case ENDS_WITH -> a.endsWith(e);
            case GREATER_THAN -> numericCompare(actual, expected) > 0;
            case LESS_THAN -> numericCompare(actual, expected) < 0;
        };
    }

    private int numericCompare(String actual, String expected) {
        try {
            double a = Double.parseDouble(actual);
            double b = Double.parseDouble(expected);
            return Double.compare(a, b);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private LeadScoreRuleResponse toRuleResponse(LeadScoreRule rule) {
        return LeadScoreRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .triggerType(rule.getTriggerType().name())
                .conditionField(rule.getConditionField())
                .conditionOperator(rule.getConditionOperator() != null ? rule.getConditionOperator().name() : null)
                .conditionValue(rule.getConditionValue())
                .points(rule.getPoints())
                .enabled(rule.isEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private LeadScoreLogResponse toLogResponse(LeadScoreLog log) {
        return LeadScoreLogResponse.builder()
                .id(log.getId())
                .contactId(log.getContactId())
                .ruleId(log.getRuleId())
                .ruleName(log.getRuleName())
                .pointsDelta(log.getPointsDelta())
                .scoreBefore(log.getScoreBefore())
                .scoreAfter(log.getScoreAfter())
                .triggerType(log.getTriggerType() != null ? log.getTriggerType().name() : null)
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }

    // Resets every contact's score to zero then replays CONTACT_CREATED rules only
    // (does not replay historical opens/clicks). Returns number of contacts processed.
    @Transactional
    public int recalculateAll() {
        UUID wsId = TenantContext.getWorkspaceId();
        List<Contact> contacts = contactRepository.findByWorkspaceId(wsId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<LeadScoreRule> creationRules = ruleRepository
                .findByWorkspaceIdAndTriggerTypeAndEnabledTrue(wsId, TriggerType.CONTACT_CREATED);

        for (Contact contact : contacts) {
            contact.setLeadScore(0);
            for (LeadScoreRule rule : creationRules) {
                if (conditionMatches(rule, contact)) {
                    int before = contact.getLeadScore();
                    int after = before + rule.getPoints();
                    contact.setLeadScore(after);

                    LeadScoreLog logEntry = LeadScoreLog.builder()
                            .contactId(contact.getId())
                            .ruleId(rule.getId())
                            .ruleName(rule.getName())
                            .pointsDelta(rule.getPoints())
                            .scoreBefore(before)
                            .scoreAfter(after)
                            .triggerType(TriggerType.CONTACT_CREATED)
                            .reason("Score recalculation")
                            .build();
                    logEntry.setWorkspaceId(wsId);
                    logRepository.save(logEntry);
                }
            }
            contactRepository.save(contact);
        }
        return contacts.size();
    }
}
