package com.leadrush.common.exception;

import lombok.Getter;

/**
 * Thrown when an unactivated user tries to log in.
 *
 * Carries lastActivationEmailSentAt so the frontend can calculate
 * the remaining cooldown time for the "Resend" button.
 */
@Getter
public class ActivationRequiredException extends BusinessException {

    private final String lastActivationEmailSentAt;

    public ActivationRequiredException(String message, String lastActivationEmailSentAt) {
        super(message);
        this.lastActivationEmailSentAt = lastActivationEmailSentAt;
    }
}
