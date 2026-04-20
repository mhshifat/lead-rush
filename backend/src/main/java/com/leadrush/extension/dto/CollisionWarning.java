package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Single teammate-activity record surfaced in the side panel's collision
 * warning card. "Sarah sent a LinkedIn message 3 days ago" → helps reps
 * avoid double-dialing a prospect an hour after a teammate did.
 */
@Value
@Builder
public class CollisionWarning {
    String userName;
    /** Task type name (LINKEDIN_MESSAGE, NOTE, CALL, MANUAL) — panel maps to a human label. */
    String action;
    /** Task status (PENDING/COMPLETED) — used to colour the chip. */
    String status;
    LocalDateTime at;
}
