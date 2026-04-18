package com.leadrush.leadscoring.entity;

/**
 * Events that can fire a lead scoring rule.
 *
 * When one of these happens, the scoring engine looks up all enabled rules
 * for the workspace matching this trigger, evaluates conditions, and applies
 * the points delta to the contact.
 */
public enum TriggerType {
    /** A new contact was created (via form, import, or manually). */
    CONTACT_CREATED,

    /** An existing contact's lifecycle stage changed (e.g., LEAD → MQL). */
    CONTACT_UPDATED,

    /** Contact opened a sequence email. */
    EMAIL_OPENED,

    /** Contact clicked a link in a sequence email. */
    EMAIL_CLICKED,

    /** Contact replied to a sequence email (Phase 3 — requires IMAP). */
    EMAIL_REPLIED,

    /** Contact submitted a form (landing page / embedded). */
    FORM_SUBMITTED,

    /** Contact was enrolled in a sequence. */
    ENROLLED
}
