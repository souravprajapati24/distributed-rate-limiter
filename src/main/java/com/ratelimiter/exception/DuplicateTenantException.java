package com.ratelimiter.exception;

public class DuplicateTenantException extends RuntimeException {

    public DuplicateTenantException(String message) {
        super(message);
    }
}