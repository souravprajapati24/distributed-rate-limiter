package com.ratelimiter.exception;

import java.util.UUID;

public class TierNotFoundException extends RuntimeException {

    public TierNotFoundException(UUID tierId) {
        super("Quota tier with ID " + tierId + " not found");
    }

    public TierNotFoundException(String tierName) {
        super("Quota tier with name '" + tierName + "' not found");
    }
}