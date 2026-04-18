package com.leadrush.sequence.entity;

public enum EnrollmentStatus {
    ACTIVE,         // Currently running through steps
    PAUSED,         // Manually paused by user
    COMPLETED,      // Finished all steps
    REPLIED,        // Contact replied — sequence auto-stopped
    BOUNCED,        // Email bounced — stopped
    UNSUBSCRIBED,   // Contact unsubscribed
    FAILED          // Fatal error (e.g., missing email)
}
