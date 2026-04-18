package com.leadrush.common.exception;

/** Base class for expected client-facing errors. Message is safe to show users. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
