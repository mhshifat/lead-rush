package com.leadrush.notification.entity;

/**
 * Semantic notification type. The frontend uses this to pick an icon + styling.
 */
public enum NotificationType {
    TASK_ASSIGNED,
    ENROLLMENT_COMPLETED,
    ENROLLMENT_BOUNCED,
    ENROLLMENT_UNSUBSCRIBED,
    FORM_SUBMITTED,
    SCORE_THRESHOLD,
    SEQUENCE_STEP_SKIPPED,
    DEAL_ASSIGNED,
    CONTACT_REPLIED,
    GENERIC
}
