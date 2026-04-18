package com.leadrush.common.exception;

/**
 * Thrown when user is not authenticated or token is invalid (401).
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
