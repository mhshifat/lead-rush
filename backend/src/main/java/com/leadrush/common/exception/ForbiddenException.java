package com.leadrush.common.exception;

/** 403 — authenticated but not permitted. */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(message);
    }
}
