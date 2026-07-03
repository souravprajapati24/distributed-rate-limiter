package com.ratelimiter.exception;

import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant with ID " + tenantId + " not found");
    }

    public TenantNotFoundException(String identifier) {
        super("Tenant not found: " + identifier);
    }
}