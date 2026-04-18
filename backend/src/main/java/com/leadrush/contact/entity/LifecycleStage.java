package com.leadrush.contact.entity;

/**
 * Tracks where a contact is in the sales funnel.
 *
 * LEAD → CONTACTED → QUALIFIED → OPPORTUNITY → CUSTOMER
 *                                                  ↓
 *                                                LOST
 */
public enum LifecycleStage {
    LEAD,           // New, uncontacted
    CONTACTED,      // We've reached out
    QUALIFIED,      // Fits our criteria (budget, authority, need, timeline)
    OPPORTUNITY,    // Active deal in pipeline
    CUSTOMER,       // Closed-won
    LOST            // Closed-lost or disqualified
}
