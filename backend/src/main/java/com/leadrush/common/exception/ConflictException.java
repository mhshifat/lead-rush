package com.leadrush.common.exception;

/** 409 — conflicts with existing state (e.g., duplicate email). */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(message);
    }
}
